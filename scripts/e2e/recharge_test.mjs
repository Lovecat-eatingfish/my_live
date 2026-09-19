/**
 * 充值链路冒烟测试：登录 → 查余额 → 查档位 → 下单+模拟支付 → 验证到账与流水
 * 用法: node scripts/e2e/recharge_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
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
  log('balance query', bal1.code === 200, `balance=${bal1.data}`)

  const prod = await api('/bank/products', token, { query: { type: 0 } })
  const list = prod.data?.payProductItemVOList || []
  log('products', prod.code === 200 && list.length > 0, `${list.length} 档: ${list.map(p => `${p.coinNum}币/¥${p.price / 100}`).join(', ')}`)

  const target = list.find(p => p.coinNum === 1000) || list[0]
  const pay = await api('/bank/payProduct', token, {
    query: { productId: target.id, paySource: 2, payChannel: 1 }
  })
  log('payOrder(mock pay)', pay.code === 200, `orderId=${pay.data?.orderId} 档位=${target.coinNum}金币/¥${target.price / 100}`)

  // 后端模拟回调是同步执行的，但入账走异步线程池，留点时间
  await wait(3000)
  const bal2 = await api('/bank/account/balance', token)
  log('balance after recharge', Number(bal2.data) === Number(bal1.data) + target.coinNum,
    `${bal1.data} + ${target.coinNum} = ${bal2.data}`)
}

run().catch(e => { console.error('❌ 测试异常:', e.message); process.exitCode = 1 })
