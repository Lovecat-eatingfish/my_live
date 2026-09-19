// 红包扣费 E2E：发送扣主播金币、余额不足拦截、结算退还
const GATEWAY = 'http://localhost:38080/live/api'
async function api(path, token, { query = {}, body = null } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  const res = await fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST',
    headers: { 'token': token, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : null
  })
  return res.json()
}
async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const login = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (login.code !== 200) throw new Error('登录失败 ' + phone + ' ' + login.msg)
  return { token: login.data.token, userId: login.data.userId }
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

;(async () => {
  let ok = true
  const assert = (name, cond, extra = '') => {
    console.log(`${cond ? 'PASS' : 'FAIL'} | ${name}${extra ? ' | ' + extra : ''}`)
    if (!cond) ok = false
  }

  // ============ 场景A：余额充足，发送扣费 ============
  const anchor = await login('13900999201')
  console.log(`主播 userId=${anchor.userId}`)
  // 充值 22800 金币（模拟支付自动回调入账）
  const recharge = await api('/bank/payProduct', anchor.token, { query: { productId: 6, paySource: 1, payChannel: 1 } })
  assert('A1 充值下单成功', recharge.code === 200)
  await sleep(2500) // 等模拟回调 + 异步入账
  const bal1 = await api('/bank/account/balance', anchor.token)
  const balanceBefore = bal1.data
  assert('A2 充值到账(余额>=22800)', balanceBefore >= 22800, `balance=${balanceBefore}`)

  // 创建红包：总额 500，10 个
  const created = await api('/gift/redpacket/create', anchor.token, {
    body: { roomId: 999, totalPrice: 500, totalCount: 10, maxGetPrice: 100 }
  })
  assert('A3 创建红包成功', created.code === 200 && created.data?.redPacketId, JSON.stringify(created.data || created.msg))
  const rpId = created.data.redPacketId

  // 预热 → 发送
  await api('/gift/redpacket/prepare', anchor.token, { body: { redPacketId: rpId } })
  await sleep(800)
  const sent = await api('/gift/redpacket/send', anchor.token, { body: { redPacketId: rpId } })
  assert('A4 发送红包成功', sent.code === 200, JSON.stringify(sent))
  await sleep(2500) // 等扣费落库
  const bal2 = await api('/bank/account/balance', anchor.token)
  assert('A5 发送后扣费500', bal2.data === balanceBefore - 500, `before=${balanceBefore} after=${bal2.data}`)

  // 验证流水类型为红包支出(2)
  // 自动结算：发送时投递的延迟1分钟消息触发，无人领取则500全额退还
  console.log('等待延迟结算消息(1分钟)...')
  await sleep(75000)
  const bal3 = await api('/bank/account/balance', anchor.token)
  assert('A6 延迟结算退还500', bal3.data === balanceBefore, `after_settle=${bal3.data}`)

  // ============ 场景B：余额不足拦截 ============
  const poor = await login('13900999202')
  console.log(`穷主播 userId=${poor.userId}`)
  const created2 = await api('/gift/redpacket/create', poor.token, {
    body: { roomId: 998, totalPrice: 500, totalCount: 10, maxGetPrice: 100 }
  })
  const rpId2 = created2.data?.redPacketId
  await api('/gift/redpacket/prepare', poor.token, { body: { redPacketId: rpId2 } })
  await sleep(800)
  const sent2 = await api('/gift/redpacket/send', poor.token, { body: { redPacketId: rpId2 } })
  assert('B1 余额不足被拦截(非200)', sent2.code !== 200, `code=${sent2.code} msg=${sent2.msg}`)
  assert('B2 提示金币余额不足', (sent2.msg || '').includes('余额不足'), sent2.msg)

  console.log(ok ? '\n全部通过 ✅' : '\n存在失败 ❌')
  process.exit(ok ? 0 : 1)
})().catch(e => { console.error('E2E异常', e); process.exit(1) })
