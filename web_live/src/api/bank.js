import request from './request'

// 充值产品列表（type为产品类型：0=旗鱼直播间产品）
export const getProducts = (type = 0) => request.post('/bank/products', null, { params: { type } })

// 发起支付（后端PayProductReqVO为表单/查询参数绑定，须走query）
// 本项目不对接真实支付渠道：后端下单后直接模拟"支付成功回调"，返回时订单已支付、金币已到账
export const payProduct = (data) => request.post('/bank/payProduct', null, { params: data })

// 当前登录用户金币余额
export const getBalance = () => request.post('/bank/account/balance')

// 对账差错明细分页查询（bizDate: yyyy-MM-dd，可为空查全部）
export const reconList = (params) => request.post('/bank/recon/list', null, { params })

// 手动触发对账（不传日期默认核对昨天）
export const reconTrigger = (bizDate) => request.post('/bank/recon/trigger', null, { params: { bizDate } })
