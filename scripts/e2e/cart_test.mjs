// 购物车专项：加购→改数量→删除→结算→清空验证
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
;(async () => {
  await api('/userLogin/sendLoginCode', '', { query: { phone: '13800138001' } })
  const login = await api('/userLogin/login', '', { query: { phone: '13800138001', code: '123456' } })
  const token = login.data.token
  const roomId = 20 // 130523的直播间(有商品1薯片/2坚果)
  let pass = 0, fail = 0
  const check = (name, ok, detail) => { console.log(`${ok ? '✅' : '❌'} [${name}] ${detail}`); ok ? pass++ : fail++ }

  // 1. 清空购物车（幂等起点）
  await api('/gift/cart/clear', token, { query: { roomId } })
  // 2. 加购 薯片x1 + 坚果x2
  let vo = await api('/gift/cart/add', token, { query: { roomId, skuId: 1, num: 1 } })
  vo = await api('/gift/cart/add', token, { query: { roomId, skuId: 2, num: 2 } })
  let d = vo.data
  check('加购', d.totalCount === 3 && d.totalPrice === 990 + 2990 * 2, `3件 合计${d.totalPrice}分`)
  // 3. 修改数量 坚果改1
  vo = await api('/gift/cart/update', token, { query: { roomId, skuId: 2, num: 1 } })
  d = vo.data
  check('改数量', d.totalCount === 2 && d.totalPrice === 990 + 2990, `2件 ${d.totalPrice}分`)
  // 4. 删除薯片
  vo = await api('/gift/cart/remove', token, { query: { roomId, skuId: 1 } })
  d = vo.data
  check('删除', d.totalCount === 1 && d.totalPrice === 2990, `1件 ${d.totalPrice}分`)
  // 5. 再加回薯片，结算
  await api('/gift/cart/add', token, { query: { roomId, skuId: 1, num: 1 } })
  vo = await api('/gift/cart/checkout', token, { query: { roomId } })
  d = vo.data
  check('结算', d.paySuccess === true && d.orderId > 0, `订单${d.orderId} 金额${d.totalPrice}分`)
  // 6. 结算后购物车清空
  vo = await api('/gift/cart/list', token, { query: { roomId } })
  check('结算后清空', vo.data.totalCount === 0, JSON.stringify(vo.data))
  // 7. 空购物车结算应报参数异常
  vo = await api('/gift/cart/checkout', token, { query: { roomId } })
  check('空车拦截', vo.code !== 200, `code=${vo.code}`)
  // 8. DB订单核对
  console.log(`\n=> 通过${pass} 失败${fail}`)
  process.exit(fail > 0 ? 1 : 0)
})()
