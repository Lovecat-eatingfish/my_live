/**
 * 批次三 E2E：排行榜（纯Redis）+ 搜索 + 通知中心
 * 1 人气榜（进房 ZINCRBY）        2 本场贡献榜（送礼）  3 主播收礼日榜/周榜
 * 4 搜索三分栏（直播/视频/用户）  5 通知：关注产生"新的粉丝"
 * 6 通知：点赞产生"收到新的点赞" 7 未读数/已读/全部已读
 * 用法: node scripts/rank_search_notify_test.mjs
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

async function wsJoin(cfg, userId, roomId) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${cfg.token}/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: 19231, code: 1001, len: loginBody.length, body: loginBody }))
  const hbBody = JSON.stringify({ appId: 10001, userId })
  const hbTimer = setInterval(() => {
    try { ws.send(JSON.stringify({ magic: 19231, code: 1004, len: hbBody.length, body: hbBody })) } catch { }
  }, 15000)
  await sleep(800)
  return { ws, close: () => { clearInterval(hbTimer); ws.close() } }
}

const run = async () => {
  const A = await login('13800138000')
  const B = await login('13900999201')
  log('login', true, `A=${A.userId} B=${B.userId}`)

  // ---- 准备：B 开播，A 进房（进房即入人气榜，走 MQ 异步） ----
  let room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '排行搜索测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '排行搜索测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const cfgA = await api('/im/getImConfig', A.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  await sleep(3500) // 进房 MQ 异步 → Set + 人气榜

  // ---- 1. 人气榜 ----
  await sleep(1500)
  const heat = await api('/rank/heat', A.token)
  const heatHit = (heat.data || []).find(r => Number(r.roomId) === Number(roomId))
  log('人气榜含测试房间', heat.code === 200 && !!heatHit, `heat=${heatHit ? heatHit.score : 0} ${heatHit ? heatHit.roomName : ''}`)

  // ---- 2+3. A 送礼 → 本场贡献榜 + 主播日榜 ----
  const gifts = await api('/gift/listGift', A.token)
  const gift = (gifts.data || [])[0]
  if (!gift) throw new Error('礼物列表为空')
  const balBefore = await api('/bank/account/balance', A.token)
  const send = await api('/gift/send', A.token, {
    body: { giftId: gift.giftId ?? gift.id, roomId, receiverId: B.userId, type: 0 }
  })
  log('送礼成功', send.code === 200, `giftId=${gift.giftId ?? gift.id} code=${send.code}`)
  await sleep(3500) // MQ 扣费 + 榜单 ZINCRBY

  const roomRank = await api('/rank/roomGift', A.token, { query: { roomId } })
  const mine = (roomRank.data || []).find(r => Number(r.userId) === Number(A.userId))
  log('本场贡献榜含A', roomRank.code === 200 && !!mine && mine.score > 0,
    `score=${mine ? mine.score : 0} nick=${mine ? mine.nickName : ''}`)

  const dayRank = await api('/rank/anchorGift', A.token, { query: { period: 'day' } })
  const anchorHit = (dayRank.data || []).find(r => Number(r.userId) === Number(B.userId))
  log('主播日榜含B', dayRank.code === 200 && !!anchorHit, `score=${anchorHit ? anchorHit.score : 0}`)
  const weekRank = await api('/rank/anchorGift', A.token, { query: { period: 'week' } })
  const weekHit = (weekRank.data || []).find(r => Number(r.userId) === Number(B.userId))
  log('主播周榜含B(7日聚合)', weekRank.code === 200 && !!weekHit, `score=${weekHit ? weekHit.score : 0}`)

  // ---- 4. 搜索三分栏（房间搜索必须在关播前：只搜开播中的房间） ----
  const sr = await api('/search/all', A.token, { query: { keyword: '排行搜索' } })
  const roomHit = (sr.data?.rooms || []).some(r => Number(r.id) === Number(roomId))
  log('搜索-直播间', sr.code === 200 && roomHit, `rooms=${sr.data?.rooms?.length}`)
  a.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })

  const sv = await api('/search/all', A.token, { query: { keyword: '关系链测试视频' } })
  log('搜索-视频', sv.code === 200 && (sv.data?.videos || []).length >= 1, `videos=${sv.data?.videos?.length}`)
  const su = await api('/search/all', A.token, { query: { keyword: String(A.userId) } })
  const userHit = (su.data?.users || []).some(u => Number(u.userId) === Number(A.userId))
  log('搜索-用户', su.code === 200 && userHit, `users=${su.data?.users?.length}`)

  // ---- 5. 关注通知 ----
  const unreadB0 = await api('/user/notify/unreadCount', B.token)
  await api('/user/unfollow', A.token, { query: { followUserId: B.userId } })
  await sleep(500)
  await api('/user/follow', A.token, { query: { followUserId: B.userId } })
  await sleep(1500)
  const unreadB1 = await api('/user/notify/unreadCount', B.token)
  log('关注产生通知', unreadB1.data === unreadB0.data + 1,
    `unread ${unreadB0.data} -> ${unreadB1.data}`)
  const nl = await api('/user/notify/list', B.token)
  const fanNotify = (nl.data?.list || []).find(n => n.title === '新的粉丝')
  log('通知列表含"新的粉丝"', !!fanNotify, fanNotify ? fanNotify.content : '无')

  // ---- 6. 点赞通知：B 发视频，A 点赞 ----
  const pub = await api('/video/publish', B.token, { body: { title: '通知测试视频', videoUrl: 'http://x/n.mp4' } })
  const videoId = pub.data
  await api('/video/like', A.token, { query: { id: videoId, isLike: true } })
  await sleep(1500)
  const nl2 = await api('/user/notify/list', B.token)
  const likeNotify = (nl2.data?.list || []).find(n => n.title === '收到新的点赞')
  log('点赞产生通知', !!likeNotify, likeNotify ? likeNotify.content : '无')
  const unreadB2 = await api('/user/notify/unreadCount', B.token)
  log('未读数累计', unreadB2.data === unreadB1.data + 1, `unread=${unreadB2.data}`)

  // ---- 7. 已读 / 全部已读 ----
  await api('/user/notify/read', B.token, { query: { notifyId: likeNotify.id } })
  const unreadB3 = await api('/user/notify/unreadCount', B.token)
  log('单条已读', unreadB3.data === unreadB2.data - 1, `unread=${unreadB3.data}`)
  await api('/user/notify/read', B.token)
  const unreadB4 = await api('/user/notify/unreadCount', B.token)
  log('全部已读', unreadB4.data === 0, `unread=${unreadB4.data}`)

  // 清理
  await api('/user/unfollow', A.token, { query: { followUserId: B.userId } })
  console.log('\n批次三 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
