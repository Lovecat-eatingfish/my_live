/**
 * 批次二（关系链）E2E：
 * 1 关注/取关/是否关注 + 计数    2 关注列表/粉丝列表
 * 3 开播推送 5567（粉丝在线收到，点击可跳）
 * 4 个人主页聚合 /user/profile   5 TA 的视频 /video/user/list
 * 6 等级体系：看播+10 弹幕+1(日限20) 发视频+50 → 升级 L2 → 5570 特效
 * 7 弹幕带 level 徽章字段
 * 8 t_user_notify 开播通知落库（mysql 直查）
 * 用法: node scripts/relation_profile_level_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const MAGIC = 19231

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
const { execSync } = await import('node:child_process')

async function wsJoin(cfg, userId, roomId) {
  const ws = new WebSocket(`ws://127.0.0.1:38115/${cfg.token}/${userId}/1001/${roomId}`)
  await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
  const loginBody = JSON.stringify({ appId: 10001, userId, token: cfg.token })
  ws.send(JSON.stringify({ magic: MAGIC, code: 1001, len: loginBody.length, body: loginBody }))
  const hbBody = JSON.stringify({ appId: 10001, userId })
  const hbTimer = setInterval(() => {
    try { ws.send(JSON.stringify({ magic: MAGIC, code: 1004, len: hbBody.length, body: hbBody })) } catch { }
  }, 15000)
  await sleep(800)
  const received = []
  ws.onmessage = (ev) => {
    try {
      const m = JSON.parse(ev.data)
      let body = m.body
      if (Array.isArray(body)) body = new TextDecoder('utf-8').decode(new Uint8Array(body))
      if (m.code === 1003 && body) received.push(JSON.parse(body))
    } catch { /* ignore */ }
  }
  return { ws, received, close: () => { clearInterval(hbTimer); ws.close() } }
}

function sendChat(ws, userId, roomId, content) {
  const body = {
    appId: 10001, userId, bizCode: 5555,
    data: JSON.stringify({ userId, content, roomId, senderName: '测试A', senderAvtar: '' })
  }
  const bodyStr = JSON.stringify(body)
  ws.send(JSON.stringify({ magic: MAGIC, code: 1003, len: bodyStr.length, body: bodyStr }))
}

async function openLivingWithRetry(token) {
  let room = await api('/living/startingLiving', token, { query: { type: 1, roomName: '关系链测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', token, { query: { type: 1, roomName: '关系链测试间', covertImg: 'x' } })
  }
  return room
}

const run = async () => {
  const A = await login('13800138000')
  const B = await login('13900999201')
  log('login', true, `A=${A.userId} B=${B.userId}`)

  // ---- 1. 关注/取关/计数 ----
  const profA0 = await api('/user/profile', A.token)
  const profB0 = await api('/user/profile', B.token, { query: { targetUserId: B.userId } })
  log('主页聚合(自己/他人)', profA0.code === 200 && profB0.code === 200,
    `A.level=${profA0.data.level} exp=${profA0.data.exp} B.fans=${profB0.data.fansCnt}`)

  // 先清理历史关注状态
  if (profA0.data.isFollow) await api('/user/unfollow', A.token, { query: { followUserId: B.userId } })
  const follow0 = await api('/user/follow', A.token, { query: { followUserId: B.userId } })
  log('关注成功', follow0.code === 200 && follow0.data === true, `code=${follow0.code} data=${follow0.data}`)
  const isf = await api('/user/isFollow', A.token, { query: { targetUserId: B.userId } })
  const profB1 = await api('/user/profile', B.token, { query: { targetUserId: B.userId } })
  log('isFollow=true & B粉丝+1', isf.data === true && profB1.data.fansCnt === profB0.data.fansCnt + 1,
    `isFollow=${isf.data} B.fans=${profB1.data.fansCnt}`)

  const unf = await api('/user/unfollow', A.token, { query: { followUserId: B.userId } })
  const isf2 = await api('/user/isFollow', A.token, { query: { targetUserId: B.userId } })
  log('取关生效', unf.data === true && isf2.data === false, `unfollow=${unf.data} isFollow=${isf2.data}`)
  // 重新关注（后续推送测试用）
  await api('/user/follow', A.token, { query: { followUserId: B.userId } })

  // ---- 2. 关注/粉丝列表 ----
  const fList = await api('/user/followList', A.token)
  const fansList = await api('/user/fansList', B.token)
  const aFollowsB = (fList.data?.list || []).some(u => Number(u.userId) === Number(B.userId))
  const bFansA = (fansList.data?.list || []).some(u => Number(u.userId) === Number(A.userId))
  log('A关注列表含B & B粉丝列表含A', aFollowsB && bFansA,
    `A关注=${fList.data?.list?.length}人 B粉丝=${fansList.data?.list?.length}人`)

  // ---- 3+4. B 开播 → A 进房连 WS → B 重开播 → A 收到 5567 ----
  let room = await openLivingWithRetry(B.token)
  if (room.code !== 200) throw new Error('B开播失败 ' + room.msg)
  let roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  const cfgA = await api('/im/getImConfig', A.token)
  const a = await wsJoin(cfgA.data, A.userId, roomId)
  await sleep(3000) // 进房异步落房间用户集合

  // 看播经验 +10（进房 MQ）已发，等结算
  await sleep(2500)
  const profA1 = await api('/user/profile', A.token)
  log('看播经验+10', profA1.data.exp >= profA0.data.exp + 10,
    `exp ${profA0.data.exp} -> ${profA1.data.exp}`)

  // ---- 6a. 发视频 +50 x1 ----
  const pub1 = await api('/video/publish', A.token, { body: { title: '关系链测试视频一', videoUrl: 'http://x/v1.mp4' } })
  await sleep(2500)
  const profA2 = await api('/user/profile', A.token)
  log('发视频经验+50', pub1.code === 200 && profA2.data.exp >= profA1.data.exp + 50,
    `exp ${profA1.data.exp} -> ${profA2.data.exp}`)
  const vid1 = pub1.data

  // ---- 5. TA 的视频 ----
  const uv = await api('/video/user/list', A.token, { query: { targetUserId: A.userId } })
  const uvHas = (uv.data || []).some(v => Number(v.id) === Number(vid1))
  log('TA的视频列表', uv.code === 200 && uvHas, `共${(uv.data || []).length}个`)

  // ---- 6b. 弹幕经验：清当日计数器后发 12 条（全部计入），弹幕带 level ----
  try {
    execSync(`docker exec redis-01 redis-cli --scan --pattern "qiyu-live-msg-provider:danmuExpDaily:${A.userId}:*" | xargs -r docker exec -i redis-01 redis-cli DEL`,
      { shell: true, encoding: 'utf-8', stdio: 'ignore' })
  } catch { /* 不影响断言，仅保证可重复 */ }
  const beforeDanmu = a.received.length
  for (let i = 0; i < 12; i++) {
    sendChat(a.ws, A.userId, roomId, `经验弹幕${i}`)
    await sleep(550)
  }
  await sleep(2500)
  const profA3 = await api('/user/profile', A.token)
  const expGain = profA3.data.exp - profA2.data.exp
  log('弹幕经验(12条全部计入，未超日限20)', expGain === 12, `exp +${expGain}`)

  // B 进房收弹幕，检查 level 字段
  const cfgB = await api('/im/getImConfig', B.token)
  const b = await wsJoin(cfgB.data, B.userId, roomId)
  await sleep(3000)
  sendChat(a.ws, A.userId, roomId, '检查等级徽章字段')
  await sleep(2500)
  const danmuWithLevel = b.received.filter(m => m.bizCode === 5555).map(m => JSON.parse(m.data))
    .find(d => String(d.content).includes('徽章'))
  log('弹幕带level徽章字段', !!danmuWithLevel && danmuWithLevel.level >= 1,
    `level=${danmuWithLevel ? danmuWithLevel.level : '无'}`)

  // ---- 6c. 升级测试（可重复）：把经验拨到"下一级门槛-50"，发视频(+50) → 恰好跨一级，5570 广播 ----
  const LV = [0, 100, 300, 600, 1000, 2000, 3500, 6000, 10000, 20000, 35000, 60000]
  const calcLv = (e) => { let l = 1; for (let i = LV.length - 1; i >= 0; i--) if (e >= LV[i]) { l = i + 1; break } return l }
  const profC = await api('/user/profile', A.token)
  const targetExp = profC.data.nextLevelExp - 50
  try {
    execSync(`D:/Mysql/bin/mysql -uroot -p123456 -e "use qiyu_live_user; UPDATE t_user_profile_ext SET exp=${targetExp}, level=${calcLv(targetExp)} WHERE user_id=${A.userId}"`,
      { shell: true, encoding: 'utf-8', stdio: 'ignore' })
  } catch { }
  await sleep(800)
  a.received.length = 0; b.received.length = 0
  await api('/video/publish', A.token, { body: { title: '关系链测试视频二', videoUrl: 'http://x/v2.mp4' } })
  await sleep(3000)
  const profA4 = await api('/user/profile', A.token)
  const lvUp = profA4.data.level === profC.data.level + 1
  const got5570 = a.received.filter(m => m.bizCode === 5570).length
    + b.received.filter(m => m.bizCode === 5570).length
  log('发视频跨级升级', lvUp, `L${profC.data.level} -> L${profA4.data.level} exp=${profA4.data.exp}`)
  log('5570升级特效', got5570 >= 1, `收到${got5570}条`)

  // ---- 3b. B 重开播 → A 收到 5567 ----
  b.close()
  await api('/living/closeLiving', B.token, { query: { roomId } })
  await sleep(11000) // 开播频控 1次/10s
  const room2 = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '关系链测试间2', covertImg: 'x' } })
  if (room2.code !== 200) {
    console.log('   重开播被频控，等 65s...')
    await sleep(65000)
    room2 = await api('/living/startingLiving', B.token, { query: { type: 1, roomName: '关系链测试间2', covertImg: 'x' } })
  }
  if (room2.code !== 200) throw new Error('重开播失败 ' + room2.msg)
  await sleep(3000) // 等推送消费者分页拉粉丝并推送
  const push = a.received.filter(m => m.bizCode === 5567).map(m => JSON.parse(m.data))
  log('开播推送5567', push.length >= 1 && Number(push[0].roomId) === Number(room2.data.roomId),
    `收到${push.length}条 roomId=${push[0] ? push[0].roomId : '-'}`)

  a.close()

  // ---- 8. t_user_notify 落库 ----
  let notifyCnt = 0
  try {
    const out = execSync(
      `D:/Mysql/bin/mysql -uroot -p123456 -N -e "use qiyu_live_user; SELECT COUNT(*) FROM t_user_notify WHERE user_id=${A.userId} AND type=4"`,
      { shell: true, encoding: 'utf-8' })
    notifyCnt = parseInt(out.trim().split('\n').pop())
  } catch (e) { /* 忽略 */ }
  log('开播通知落库t_user_notify', notifyCnt >= 2, `A收到开播通知=${notifyCnt}条(两次开播)`)

  // 清理
  await api('/living/closeLiving', B.token, { query: { roomId: room2.data.roomId } })
  await api('/user/unfollow', A.token, { query: { followUserId: B.userId } })
  console.log('\n批次二 E2E 结束')
}

run().catch(e => { console.error('❌ 测试异常:', e); process.exitCode = 1 })
