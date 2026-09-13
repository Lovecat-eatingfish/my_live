/**
 * 批次十一 E2E：直播分区体系 + 首页关注 tab
 * 1 分区列表=4 个种子分区  2 admin 新增分区→C 端可见  3 停用分区→C 端隐藏  4 重名被拒
 * 5 B 开播+A 关注→followRooms 含 B 房间  6 A 取关→followRooms 不含
 * 用法: node scripts/category_follow_test.mjs
 */
const GATEWAY = 'http://localhost:38080/live/api'
const ADMIN = 'http://localhost:38100/live/admin'

function log(step, ok, detail = '') {
  console.log(`${ok ? '✅' : '❌'} [${step}] ${detail}`)
  if (!ok) process.exitCode = 1
}

async function api(path, token, { query = {} } = {}) {
  const qs = Object.entries(query).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  return fetch(`${GATEWAY}${path}${qs ? '?' + qs : ''}`, {
    method: 'POST', headers: { 'token': token, 'Content-Type': 'application/json' }
  }).then(r => r.json())
}

async function adminApi(path, token, params = {}) {
  const qs = Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
  return fetch(`${ADMIN}${path}${qs ? '?' + qs : ''}`, { method: 'POST', headers: { adminToken: token } })
    .then(r => r.json())
}

async function login(phone) {
  await api('/userLogin/sendLoginCode', '', { query: { phone } })
  const vo = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
  if (vo.code !== 200) throw new Error(`登录失败: ${vo.msg}`)
  return { token: vo.data.token, userId: vo.data.userId }
}

const sleep = (ms) => new Promise(r => setTimeout(r, ms))

const run = async () => {
  const A = await login('13876543210')
  const B = await login('13876543211')
  const AL = await fetch(`${ADMIN}/auth/login?username=admin&password=admin123`, { method: 'POST' }).then(r => r.json())
  if (AL.code !== 200) throw new Error('admin 登录失败')
  const adminToken = AL.data?.token || AL.data
  log('login', true, `A=${A.userId} B=${B.userId}`)

  // ---- 1. 种子分区 ----
  let vo = await api('/living/categories', A.token)
  const base = (vo.data || []).map(c => c.name)
  log('种子分区4个', vo.code === 200 && base.length === 4, base.join('/'))

  // ---- 2. admin 新增分区 ----
  const catName = '音乐' + Date.now() % 100
  vo = await adminApi('/living/category/add', adminToken, { name: catName, icon: '🎵', sort: 5 })
  log('admin新增分区', vo.code === 200 && vo.data?.id > 0, `id=${vo.data?.id} name=${catName}`)
  const newId = vo.data?.id
  vo = await api('/living/categories', A.token)
  log('C端可见新分区', (vo.data || []).some(c => c.id === newId), `共${(vo.data || []).length}个`)

  // ---- 4. 重名被拒 ----
  vo = await adminApi('/living/category/add', adminToken, { name: catName, icon: '', sort: 9 })
  log('重名被拒', vo.code !== 200, `code=${vo.code}`)

  // ---- 3. 停用分区 → C 端隐藏 ----
  vo = await adminApi('/living/category/update', adminToken, { id: newId, status: 0 })
  log('admin停用分区', vo.code === 200 && vo.data === true)
  vo = await api('/living/categories', A.token)
  log('C端不可见停用分区', !(vo.data || []).some(c => c.id === newId), `共${(vo.data || []).length}个`)

  // ---- 5. followRooms ----
  let room = await api('/living/startingLiving', B.token, { query: { type: 2, roomName: '分区测试间', covertImg: 'x' } })
  if (room.code !== 200) {
    console.log('   开播被频控，等 65s 重试...')
    await sleep(65000)
    room = await api('/living/startingLiving', B.token, { query: { type: 2, roomName: '分区测试间', covertImg: 'x' } })
  }
  if (room.code !== 200) throw new Error('开播失败 ' + room.msg)
  const roomId = room.data.roomId
  log('B开播', true, `roomId=${roomId}`)

  vo = await api('/living/followRooms', A.token)
  const beforeFollow = (vo.data?.list || []).some(r => r.id === roomId)
  log('未关注时不含B房间', !beforeFollow, `列表${(vo.data?.list || []).length}个`)

  vo = await api('/user/follow', A.token, { query: { followUserId: B.userId } })
  log('A关注B', vo.code === 200)
  await sleep(500)
  vo = await api('/living/followRooms', A.token)
  log('关注后followRooms含B房间', (vo.data?.list || []).some(r => r.id === roomId),
    `列表${(vo.data?.list || []).length}个`)

  // ---- 6. 取关后不含 ----
  vo = await api('/user/unfollow', A.token, { query: { followUserId: B.userId } })
  vo = await api('/living/followRooms', A.token)
  log('取关后不含B房间', !(vo.data?.list || []).some(r => r.id === roomId))

  await api('/living/closeLiving', B.token, { query: { roomId } })
  console.log('\n批次十一 E2E 结束')
}

run().catch(e => { console.error('❌ 异常:', e.message); process.exitCode = 1 })
