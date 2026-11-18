#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
市场化电力声明（凭证逐期归属）验收回放：
  C1 种子基线：声明册恒等式、跨区不可匹配量、重复上传冻结、批次池余额
  C2 批次池扣减：跨成员拆分留痕、余额耗尽、缺口不抵销、重复主张拦截
  C3 月度错配 + 跨区凭证：计入不可匹配量，不抵减剩余用电
  C4 冻结未决与重复上传裁决：FROZEN_PENDING 不形成覆盖，裁决后解冻可声明
  C5 逐张部分拆分：多链累计不超凭证电量，超余额主张被拦截
  C6 报告后撤销：新声明停用，已发布报告保留声明链并标记后续失效
  C7 凭证转让：持有人变更、原链失效保留、停用后新声明拦截
  C8 恒等式与权限：total = matched + residual；越权 403；用电账唯一
前置：后端已启动（默认 http://127.0.0.1:8080），数据库为初始种子状态。
"""
import json
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8080'
API = BASE + '/api'
PASSED, FAILED = [], []


def req(method, path, body=None, token=None):
    data = json.dumps(body).encode('utf-8') if body is not None else None
    r = urllib.request.Request(API + path, data=data, method=method)
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('X-Auth-Token', token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode('utf-8')
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8')
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, {'error': raw}


def check(name, cond, detail=''):
    if cond:
        PASSED.append(name)
        print(f'  [PASS] {name}')
    else:
        FAILED.append((name, detail))
        print(f'  [FAIL] {name} :: {detail}')


def near(a, b, eps=0.01):
    return abs(float(a) - float(b)) < eps


def login(u, p):
    return req('POST', '/auth/login', {'username': u, 'password': p})[1]['token']


def stmt_of(token, plant_id, period):
    return next(s for s in req('GET', '/claims/statements', token=token)[1]
                if s['plantId'] == plant_id and s['period'] == period)


def ledger_of(token, plant_id, period):
    return next(l for l in req('GET', '/claims/ledgers', token=token)[1]
                if l['plantId'] == plant_id and l['period'] == period)


def cert_of(token, serial):
    return next(c for c in req('GET', '/certificates', token=token)[1] if c['serialNo'] == serial)


def main():
    print(f'== 目标: {BASE}')
    admin = login('admin', 'admin123')
    analyst = login('analyst', 'analyst123')
    auditor = login('auditor', 'auditor123')

    # ---------- C1 种子基线 ----------
    print('\n[C1] 种子基线：声明册/批次池/重复冻结')
    sz_may = stmt_of(admin, 1, '2025-05')
    check('C1 苏州5月 可匹配 900000（600000+300000 逐张）', near(sz_may['matchedKwh'], 900000), str(sz_may))
    check('C1 苏州5月 跨区凭证计入不可匹配 100000', near(sz_may['unmatchedKwh'], 100000), str(sz_may))
    check('C1 苏州5月 剩余 200000（不可匹配不抵销）', near(sz_may['residualKwh'], 200000), str(sz_may))
    check('C1 苏州5月 声明册随报告发布 PUBLISHED', sz_may['status'] == 'PUBLISHED', sz_may['status'])
    cd_may = stmt_of(admin, 2, '2025-05')
    check('C1 成都5月 全额匹配 500000、剩余 0',
          near(cd_may['matchedKwh'], 500000) and near(cd_may['residualKwh'], 0), str(cd_may))
    sz_jun = stmt_of(admin, 1, '2025-06')
    check('C1 苏州6月 重复主张冻结 120000', near(sz_jun['duplicateClaimKwh'], 120000), str(sz_jun))
    check('C1 苏州6月 初始无覆盖、剩余=总量 1300000',
          near(sz_jun['matchedKwh'], 0) and near(sz_jun['residualKwh'], 1300000), str(sz_jun))
    cd_jun = stmt_of(admin, 2, '2025-06')
    check('C1 成都6月 水电凭证已匹配 150000', near(cd_jun['matchedKwh'], 150000), str(cd_jun))
    pool = req('GET', '/claims/pools', token=admin)[1][0]
    check('C1 东部太阳能池 600000 可扣、混池键正确',
          near(pool['availableKwh'], 600000) and pool['region'] == 'CN-EAST'
          and pool['generationMonth'] == '2025-06' and pool['technology'] == 'SOLAR', str(pool))
    check('C1 存在 1 条未决重复上传',
          len(req('GET', '/certificates/duplicates?status=PENDING', token=admin)[1]) == 1)
    dup_cert = cert_of(admin, 'REC-DUP-001')
    check('C1 重复竞争凭证状态 FROZEN、冻结量 120000',
          dup_cert['status'] == 'FROZEN' and near(dup_cert['frozenKwh'], 120000), str(dup_cert))

    sz_jun_ledger = ledger_of(admin, 1, '2025-06')

    # ---------- C2 批次池扣减 ----------
    print('\n[C2] 批次池扣减：跨成员拆分、缺口、重复主张拦截')
    s, b = req('POST', '/claims/allocations',
               {'method': 'POOL', 'ledgerId': sz_jun_ledger['id'], 'poolId': pool['id'], 'quantityKwh': 700000},
               token=analyst)
    check('C2 池扣减请求 700000 实际授予 600000', s == 200 and near(b['grantedKwh'], 600000), str(b)[:200])
    check('C2 缺口 100000 明确提示且不抵销', near(b['shortfallKwh'], 100000), str(b.get('shortfallKwh')))
    chains = [(a['certificate']['serialNo'], a['allocation']['allocatedKwh']) for a in b['allocations']]
    check('C2 池扣减跨成员逐张留痕 400000+200000',
          chains == [('REC-SZ-101', 400000.0), ('REC-SZ-102', 200000.0)], str(chains))
    pool2 = req('GET', f"/claims/pools/{pool['id']}", token=admin)[1]
    check('C2 池余额耗尽、成员 used 分别 400000/200000',
          near(pool2['pool']['availableKwh'], 0)
          and [m['member']['usedKwh'] for m in pool2['members']] == [400000.0, 200000.0])
    member_cert_id = pool2['members'][0]['certificate']['id']
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': member_cert_id, 'quantityKwh': 1000}, token=analyst)
    check('C2 池成员凭证再主张被拦截(409) 重复主张控制', s == 409, f'{s} {b}')
    sz_jun = stmt_of(admin, 1, '2025-06')
    check('C2 扣减后可匹配 600000、剩余 700000',
          near(sz_jun['matchedKwh'], 600000) and near(sz_jun['residualKwh'], 700000), str(sz_jun))

    # ---------- C3 月度错配 + 跨区 ----------
    print('\n[C3] 时间错配与区域错配：不可匹配量不抵销')
    month_cert = cert_of(admin, 'REC-MONTH-001')
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': month_cert['id'], 'quantityKwh': 80000}, token=analyst)
    check('C3 月度错配链 PERIOD_MISMATCH',
          s == 200 and b['allocations'][0]['allocation']['matchStatus'] == 'PERIOD_MISMATCH', str(b)[:200])
    s, b = req('POST', '/certificates',
               {'serialNo': 'REC-XREG-C3', 'holderPlantId': 1, 'region': 'CN-WEST', 'technology': 'SOLAR',
                'generationMonth': '2025-06', 'deliveryFrom': '2025-06-01', 'deliveryTo': '2025-06-30',
                'quantityKwh': 60000}, token=analyst)
    xcert = b['certificate']
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': xcert['id'], 'quantityKwh': 60000}, token=analyst)
    check('C3 跨区链 REGION_MISMATCH 且不增加覆盖',
          b['allocations'][0]['allocation']['matchStatus'] == 'REGION_MISMATCH'
          and near(b['ledger']['coveredKwh'], 600000), str(b)[:200])
    sz_jun = stmt_of(admin, 1, '2025-06')
    check('C3 不可匹配量累计 140000（80000 错月 + 60000 跨区）',
          near(sz_jun['unmatchedKwh'], 140000), str(sz_jun))
    check('C3 剩余量不被不可匹配凭证抵减，仍为 700000', near(sz_jun['residualKwh'], 700000), str(sz_jun))

    # ---------- C4 冻结未决与裁决 ----------
    print('\n[C4] 冻结未决 → 裁决 → 解冻声明')
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': dup_cert['id'], 'quantityKwh': 50000}, token=analyst)
    check('C4 冻结凭证主张登记 FROZEN_PENDING 不形成覆盖',
          s == 200 and b['allocations'][0]['allocation']['matchStatus'] == 'FROZEN_PENDING'
          and near(b['ledger']['coveredKwh'], 600000), str(b)[:200])
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': dup_cert['id'], 'quantityKwh': 10000}, token=analyst)
    check('C4 未决期间重复发起被拦截(409)', s == 409, f'{s} {b}')
    dup = req('GET', '/certificates/duplicates?status=PENDING', token=admin)[1][0]
    s, b = req('POST', f"/certificates/duplicates/{dup['id']}/resolve",
               {'winner': 'FIRST', 'reason': '序列号归属首张持有人'}, token=auditor)
    check('C4 裁决首张有效 FIRST_WON', s == 200 and b['resolution'] == 'FIRST_WON', str(b))
    dup_cert = cert_of(admin, 'REC-DUP-001')
    check('C4 裁决后凭证解冻 ACTIVE、冻结量归零、可声明 120000',
          dup_cert['status'] == 'ACTIVE' and near(dup_cert['frozenKwh'], 0), str(dup_cert))
    detail = req('GET', f"/certificates/{dup_cert['id']}", token=admin)[1]
    check('C4 裁决后可声明余额恢复 120000', near(detail['availableKwh'], 120000), str(detail['availableKwh']))
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': dup_cert['id'], 'quantityKwh': 120000}, token=analyst)
    check('C4 解冻后声明 MATCHED 120000',
          s == 200 and b['allocations'][0]['allocation']['matchStatus'] == 'MATCHED'
          and near(b['grantedKwh'], 120000), str(b)[:200])
    sz_jun = stmt_of(admin, 1, '2025-06')
    check('C4 可匹配升至 720000、重复冻结清零',
          near(sz_jun['matchedKwh'], 720000) and near(sz_jun['duplicateClaimKwh'], 0), str(sz_jun))

    # ---------- C5 逐张部分拆分 ----------
    print('\n[C5] 逐张分配部分拆分与余额控制')
    s, b = req('POST', '/certificates',
               {'serialNo': 'REC-SPLIT-001', 'holderPlantId': 1, 'region': 'CN-EAST', 'technology': 'WIND',
                'generationMonth': '2025-06', 'deliveryFrom': '2025-06-01', 'deliveryTo': '2025-06-30',
                'quantityKwh': 100000}, token=analyst)
    split_cert = b['certificate']
    req('POST', '/claims/allocations',
        {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
         'certificateId': split_cert['id'], 'quantityKwh': 30000}, token=analyst)
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': split_cert['id'], 'quantityKwh': 45000}, token=analyst)
    check('C5 同凭证二次分配成功（部分拆分多链）', s == 200 and near(b['grantedKwh'], 45000), str(b)[:160])
    detail = req('GET', f"/certificates/{split_cert['id']}", token=admin)[1]
    check('C5 拆分后可声明余额 25000、存在两条 MATCHED 链',
          near(detail['availableKwh'], 25000)
          and sum(1 for a in detail['allocations']
                  if a['allocation']['matchStatus'] == 'MATCHED') == 2, str(detail['availableKwh']))
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': split_cert['id'], 'quantityKwh': 999999}, token=analyst)
    check('C5 超额申请按余额截到 25000 并报缺口',
          s == 200 and near(b['grantedKwh'], 25000) and near(b['shortfallKwh'], 974999), str(b)[:200])
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': split_cert['id'], 'quantityKwh': 1000}, token=analyst)
    check('C5 余额耗尽后再主张被拦截(409)', s == 409, f'{s} {b}')

    # ---------- C6 报告后撤销 ----------
    print('\n[C6] 已发布报告后的凭证撤销：链保留并标记失效')
    may_cert = cert_of(admin, 'REC-SZ-002')
    s, b = req('POST', f"/certificates/{may_cert['id']}/revoke",
               {'reason': '发证机构核查发电量不实'}, token=auditor)
    check('C6 撤销成功 REVOKED', s == 200 and b['status'] == 'REVOKED', str(b))
    sz_may = stmt_of(admin, 1, '2025-05')
    check('C6 已发布声明册状态仍 PUBLISHED（不改写报告）', sz_may['status'] == 'PUBLISHED', sz_may['status'])
    check('C6 事后失效链量 300000、可匹配降至 600000',
          near(sz_may['invalidatedChainKwh'], 300000) and near(sz_may['matchedKwh'], 600000), str(sz_may))
    check('C6 剩余用电回补为 500000（失效链不再覆盖）', near(sz_may['residualKwh'], 500000), str(sz_may))
    may_ledger = ledger_of(admin, 1, '2025-05')
    md = req('GET', f"/claims/ledgers/{may_ledger['id']}", token=admin)[1]
    revoked_chain = next(a for a in md['allocations'] if a['certificate']['serialNo'] == 'REC-SZ-002')
    check('C6 原声明链保留且 chainValid=false 并带失效原因',
          revoked_chain['allocation']['chainValid'] is False
          and '撤销' in (revoked_chain['allocation']['invalidatedReason'] or ''), str(revoked_chain))
    check('C6 失效链保留报告版本号（可追溯当时声明）',
          revoked_chain['allocation']['reportVersionId'] is not None)
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': may_ledger['id'],
                'certificateId': may_cert['id'], 'quantityKwh': 1000}, token=analyst)
    check('C6 撤销后新声明停用(409)', s == 409, f'{s} {b}')
    reports = req('GET', '/reports', token=admin)[1]
    may_report = next(r for r in reports if r['period'] == '2025-05')
    rd = req('GET', f"/reports/{may_report['id']}", token=admin)[1]
    check('C6 已发布报告版本仍为 PUBLISHED 未被改写',
          rd['versions'][0]['status'] == 'PUBLISHED', str(rd['versions'][0]['status']))
    vd = req('GET', f"/reports/versions/{rd['versions'][0]['id']}", token=admin)[1]
    check('C6 报告版本详情附带声明册', len(vd['claimStatements']) >= 2, str(len(vd['claimStatements'])))

    # ---------- C7 转让 ----------
    print('\n[C7] 凭证转让：持有人变更、原链失效、停用拦截')
    cd_cert = cert_of(admin, 'REC-CD-101')
    s, b = req('POST', f"/certificates/{cd_cert['id']}/transfer",
               {'toPlantId': 1, 'reason': '集团内部调拨'}, token=analyst)
    check('C7 转让成功、持有人变更为苏州工厂(1)',
          s == 200 and b['status'] == 'TRANSFERRED' and b['holderPlantId'] == 1, str(b))
    cd_jun = stmt_of(admin, 2, '2025-06')
    check('C7 成都声明册原链失效 150000、剩余回补 400000',
          near(cd_jun['invalidatedChainKwh'], 150000) and near(cd_jun['residualKwh'], 400000)
          and near(cd_jun['matchedKwh'], 0), str(cd_jun))
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': sz_jun_ledger['id'],
                'certificateId': cd_cert['id'], 'quantityKwh': 1000}, token=analyst)
    check('C7 转让后新声明停用(409)', s == 409, f'{s} {b}')
    cd_ledger = ledger_of(admin, 2, '2025-06')
    s, b = req('POST', '/claims/allocations',
               {'method': 'CERTIFICATE', 'ledgerId': cd_ledger['id'],
                'certificateId': cd_cert['id'], 'quantityKwh': 1000}, token=analyst)
    check('C7 他厂凭证声明本厂用电拒绝(400)', s == 400, f'{s} {b}')

    # ---------- C8 恒等式与权限 ----------
    print('\n[C8] 声明册恒等式、用电账唯一、角色权限')
    ok = True
    for st in req('GET', '/claims/statements', token=admin)[1]:
        if not near(st['matchedKwh'] + st['residualKwh'], st['totalKwh']):
            ok = False
            print('    恒等式不符:', st)
    check('C8 全部声明册满足 total = matched + residual', ok)
    s, b = req('POST', '/claims/ledgers',
               {'plantId': 1, 'period': '2025-06', 'totalKwh': 1}, token=analyst)
    check('C8 同工厂同期间用电账唯一(409)', s == 409, f'{s} {b}')
    s, b = req('POST', f"/certificates/{may_cert['id']}/revoke", {'reason': '越权测试'}, token=analyst)
    check('C8 分析员撤销凭证越权(403)', s == 403, f'{s} {b}')
    s, b = req('POST', f"/certificates/duplicates/{dup['id']}/resolve",
               {'winner': 'FIRST', 'reason': 'x'}, token=analyst)
    check('C8 分析员裁决重复上传越权(403)', s == 403, f'{s} {b}')

    # ---------- 汇总 ----------
    print('\n========================================')
    print(f'通过 {len(PASSED)} 项，失败 {len(FAILED)} 项')
    if FAILED:
        for name, detail in FAILED:
            print(f'  失败: {name} :: {detail}')
        sys.exit(1)
    print('市场化电力声明验收场景全部通过 ✔')


if __name__ == '__main__':
    main()
