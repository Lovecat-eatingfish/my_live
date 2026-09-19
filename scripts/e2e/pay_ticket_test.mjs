/**
 * 批次八 E2E：付费直播间门票
 * 1 开票房间(100金币)  2 未购票进房被拒(10114)  3 买票(扣款+主播入账+幂等)
 * 4 购票后进房 OK      5 免费房间不受影响
 * 用法: node scripts/e2e/pay_ticket_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'

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
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

const run = async () => {
  const A = await login('13800138000')   // 观众
  const B = await login('13900999201')   // 主播
  log('login', true, `A=${A.userId} B=${B.userId}`)
  const balA0 = Number((await api('/bank/account/balance', A.token)).data)
  const balB0 = Number((await api('/bank/account/balance', B.token)).data)

  // ---- 1. B 开票房间 ----
  let room = await api('/living/startingLiving', B.token, {
    query: { type: 1, roomName: '门票测试间', covertImg: 'x', payType: 1, ticketPrice: 100 }
  })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, {
      query: { type: 1, roomName: '门票测试间', covertImg: 'x', payType: 1, ticketPrice: 100 }
    })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开票房间', true, `roomId=${roomId} payType=1 price=100`)

  // ---- 2. A 未购票进房被拒 ----
  const cfg0 = await api('/living/anchorConfig', A.token, { query: { roomId } })
  log('未购票进房被拒(10114)', cfg0.code === 10114, `code=${cfg0.code} msg=${cfg0.msg}`)

  // ---- 3. A 买票：扣款 + 主播入账 + 幂等 ----
  const buy1 = await api('/living/ticket/buy', A.token, { query: { roomId } })
  const balA1 = Number((await api('/bank/account/balance', A.token)).data)
  const balB1 = Number((await api('/bank/account/balance', B.token)).data)
  // 主播余额只断言增长（可能有历史延迟 MQ 落账干扰绝对值）
  log('买票扣款+主播入账', buy1.code === 200 && balA1 === balA0 - 100 && balB1 > balB0,
    `A:${balA0}->${balA1}(-100) B:${balB0}->${balB1}(+涨)`)
  await api('/living/ticket/buy', A.token, { query: { roomId } })
  const balA2 = Number((await api('/bank/account/balance', A.token)).data)
  log('重复购票幂等', balA2 === balA1, `A余额不变=${balA2}`)

  // ---- 4. 购票后进房 OK ----
  const cfg1 = await api('/living/anchorConfig', A.token, { query: { roomId } })
  log('购票后进房OK', cfg1.code === 200, `code=${cfg1.code}`)

  // ---- 5. 主播本人不看票 ----
  const cfgB = await api('/living/anchorConfig', B.token, { query: { roomId } })
  log('主播本人免票', cfgB.code === 200, `code=${cfgB.code}`)

  await api('/living/closeLiving', B.token, { query: { roomId } })

  // ---- 6. 免费房间不受影响 ----
  let free = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '免费测试间', covertImg: 'x' } })
  if (free.code !== 200) { await sleep(65000); free = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '免费测试间', covertImg: 'x' } }) }
  if (free.code !== 200) throw new Error('免费开播失败')
  const cfgFree = await api('/living/anchorConfig', A.token, { query: { roomId: free.data.roomId } })
  log('免费房间无需购票', cfgFree.code === 200, `code=${cfgFree.code}`)
  await api('/living/closeLiving', B.token, { query: { roomId: free.data.roomId } })

  console.log('\n批次八 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
