// 红包池专项：多个新用户领取，验证池子总额精确、不超发
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
const MAGIC = 19231
const RED_PACKET_ID = Number(process.argv[2] || 13)
const ROOM_ID = Number(process.argv[3] || 33)

;(async () => {
  let total = 0, count = 0
  for (let i = 0; i < 12; i++) {
    const phone = '13900310' + String(100 + i)
    await api('/userLogin/sendLoginCode', '', { query: { phone } })
    const login = await api('/userLogin/login', '', { query: { phone, code: '123456' } })
    if (login.code !== 200) { console.log('登录失败', phone, login.msg); continue }
    const token = login.data.token
    const cfg = await api('/im/getImConfig', token)
    const ws = new WebSocket(`ws://127.0.0.1:38115/${cfg.data.token}/${login.data.userId}/1001/${ROOM_ID}`)
    await new Promise(r => { ws.onopen = r; ws.onerror = () => r() })
    await new Promise(r => setTimeout(r, 600))
    const vo = await api('/gift/redpacket/receive', token, { body: { redPacketId: RED_PACKET_ID, roomId: ROOM_ID } })
    if (vo.code === 200 && vo.data > 0) { total += vo.data; count++; console.log(`用户${login.data.userId} 领到 ${vo.data}`) }
    else console.log(`用户${login.data.userId} 领取被拦截(${vo.code})`)
    ws.close()
  }
  console.log(`=> ${count}人领取成功, 合计${total}抖币 (期望: 池内剩余9个共95币)`)
  process.exit(0)
})()
