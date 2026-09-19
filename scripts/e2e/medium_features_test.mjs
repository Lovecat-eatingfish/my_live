// 中小需求批量E2E：开播类型选择 / 个人设置 / 在线观众数 / 主播商品管理
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
let ok = true
const assert = (name, cond, extra = '') => {
  console.log(`${cond ? 'PASS' : 'FAIL'} | ${name}${extra ? ' | ' + extra : ''}`)
  if (!cond) ok = false
}

;(async () => {
  const anchor = await login('13900999301')

  // ===== 1. 个人设置：改昵称 =====
  const up = await api('/user/updateProfile', anchor.token, { query: { nickName: '测试昵称A' } })
  assert('1.1 修改昵称成功', up.code === 200 && up.data === true, JSON.stringify(up))
  const me = await api('/home/initPage', anchor.token)
  assert('1.2 昵称已生效', me.data?.nickName === '测试昵称A', `nickName=${me.data?.nickName}`)

  // ===== 2. 开播带类型：type=3(赛事) =====
  const start = await api('/living/startingLiving', anchor.token,
    { query: { type: 3, roomName: '赛事测试房', covertImg: 'http://localhost:3000/minio/qiyu-live-images/test-cover.jpg' } })
  assert('2.1 开播成功', start.code === 200 && start.data?.roomId, JSON.stringify(start.data || start.msg))
  const roomId = start.data.roomId

  // ===== 3. 在线观众数 =====
  const cnt = await api('/living/onlineCount', anchor.token, { query: { roomId } })
  assert('3.1 查询在线人数成功(>=0)', cnt.code === 200 && Number.isInteger(cnt.data), `count=${cnt.data}`)

  // ===== 4. 主播商品管理 =====
  const skus = await api('/gift/sku/list', anchor.token)
  assert('4.1 商品列表', skus.code === 200 && Array.isArray(skus.data) && skus.data.length >= 3, `n=${skus.data?.length}`)
  const on1 = await api('/gift/shop/updateStatus', anchor.token, { query: { skuId: 1, status: 1 } })
  const on2 = await api('/gift/shop/updateStatus', anchor.token, { query: { skuId: 2, status: 1 } })
  assert('4.2 上架两个商品', on1.code === 200 && on2.code === 200)
  const my = await api('/gift/shop/myList', anchor.token)
  const ids = (my.data || []).map(s => s.skuId).sort()
  assert('4.3 我的货架=sku1,2', JSON.stringify(ids) === JSON.stringify([1, 2]), JSON.stringify(ids))
  const off = await api('/gift/shop/updateStatus', anchor.token, { query: { skuId: 1, status: 0 } })
  assert('4.4 下架sku1成功', off.code === 200)
  const my2 = await api('/gift/shop/myList', anchor.token)
  const ids2 = (my2.data || []).map(s => s.skuId)
  assert('4.5 下架后货架只剩sku2', JSON.stringify(ids2) === JSON.stringify([2]), JSON.stringify(ids2))

  // 清理：关闭测试直播间
  await api('/living/closeLiving', anchor.token, { query: { roomId } })

  console.log(ok ? '\n全部通过 ✅' : '\n存在失败 ❌')
  process.exit(ok ? 0 : 1)
})().catch(e => { console.error('E2E异常', e); process.exit(1) })
