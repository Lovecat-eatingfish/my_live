import request from './request'

// 礼物列表
export const listGift = () => request.post('/gift/listGift')

// 发送礼物
export const sendGift = (data) => request.post('/gift/send', data)

// ==================== 红包雨 ====================

// 主播创建红包雨（返回 redPacketId/configCode）
export const createRedPacket = (data) => request.post('/gift/redpacket/create', data)

// 主播预热红包雨
export const prepareRedPacket = (data) => request.post('/gift/redpacket/prepare', data)

// 主播发送红包雨（触发全房间红包雨）
export const sendRedPacket = (data) => request.post('/gift/redpacket/send', data)

// 用户领取红包雨，返回领取金额
export const receiveRedPacket = (data) => request.post('/gift/redpacket/receive', data)

// 查询红包雨状态
export const queryRedPacket = (data) => request.post('/gift/redpacket/query', data)

// ==================== 直播带货 ====================

// 小黄车商品列表
export const listShop = (roomId) => request.post('/gift/shop/list', null, { params: { roomId } })

// 全部在架商品（主播商品管理）
export const listAllSkus = () => request.post('/gift/sku/list')

// 我（主播）已上架的商品
export const listMyShop = () => request.post('/gift/shop/myList')

// 主播上架/下架商品（status: 1上架 0下架）
export const updateShopStatus = (data) => request.post('/gift/shop/updateStatus', null, { params: data })

// 创建商品订单
export const createOrder = (data) => request.post('/gift/order/create', data)

// 支付订单
export const payOrder = (data) => request.post('/gift/order/pay', data)

// 我的订单列表
export const listMyOrders = () => request.post('/gift/order/list')

// ==================== 购物车（直播间维度） ====================

// 加入购物车
export const cartAdd = ({ roomId, skuId, num = 1 }) =>
  request.post('/gift/cart/add', null, { params: { roomId, skuId, num } })

// 修改购物车商品数量（num<=0 移除）
export const cartUpdate = ({ roomId, skuId, num }) =>
  request.post('/gift/cart/update', null, { params: { roomId, skuId, num } })

// 移除购物车商品
export const cartRemove = ({ roomId, skuId }) =>
  request.post('/gift/cart/remove', null, { params: { roomId, skuId } })

// 查询购物车（items/totalCount/totalPrice）
export const cartList = (roomId) => request.post('/gift/cart/list', null, { params: { roomId } })

// 购物车结算（下单+支付+清空购物车）
export const cartCheckout = (roomId) => request.post('/gift/cart/checkout', null, { params: { roomId } })
