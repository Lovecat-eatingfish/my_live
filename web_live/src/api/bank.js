import request from './request'

// 充值产品列表（type为产品类型：0=旗鱼直播间产品）
export const getProducts = (type = 0) => request.post('/bank/products', null, { params: { type } })

// 发起支付（后端PayProductReqVO为表单/查询参数绑定，须走query）
export const payProduct = (data) => request.post('/bank/payProduct', null, { params: data })
