/**
 * 支付回调幂等测试：充值一次 → 直接向 bank-api 重放同一订单回调 2 次 → 余额不得再涨
 * 用法: node scripts/e2e/paynotify_idempotent_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const BANK_API_NOTIFY = 'http://localhost:38095/live/bank/payNotify/wxNotify'
const PHONE = '13800138000'

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

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
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return vo.data
}

const wait = (ms) => new Promise(r => setTimeout(r, ms))

const run = async () => {
  const data = await login(PHONE)
  const token = data.token
  log('login', true, `userId=${data.userId}`)

  const bal1 = await api('/bank/account/balance', token)
  log('balance before', bal1.code === 200, `balance=${bal1.data}`)

  const prod = await api('/bank/products', token, { query: { type: 0 } })
  const list = prod.data?.payProductItemVOList || []
  const target = list.find(p => p.coinNum === 1000) || list[0]

  const pay = await api('/bank/payProduct', token, {
    query: { productId: target.id, paySource: 2, payChannel: 1 }
  })
  log('payOrder(mock pay)', pay.code === 200, `orderId=${pay.data?.orderId} 档位=${target.coinNum}金币`)

  await wait(3000)
  const bal2 = await api('/bank/account/balance', token)
  const expected = Number(bal1.data) + target.coinNum
  log('balance after 1st notify', Number(bal2.data) === expected, `${bal1.data} + ${target.coinNum} = ${bal2.data}`)

  // 重放同一订单的回调两次（模拟第三方重复回调）
  const param = JSON.stringify({ orderId: pay.data.orderId, userId: data.userId, bizCode: 10001 })
  for (let i = 1; i <= 2; i++) {
    const res = await fetch(`${BANK_API_NOTIFY}?param=${encodeURIComponent(param)}`, { method: 'POST' })
    const body = await res.text()
    log(`replay notify #${i} accepted`, body.includes('success'), `响应=${body}（幂等时也应返回 success）`)
  }

  await wait(3000)
  const bal3 = await api('/bank/account/balance', token)
  log('balance unchanged after replays', Number(bal3.data) === expected,
    `期望=${expected} 实际=${bal3.data} ${Number(bal3.data) === expected ? '(无重复入账)' : '(重复入账！幂等失效)'}`)
}

run().catch(e => { console.error('❌ 测试异常:', e.message); process.exitCode = 1 })
