import request from './request'

// 充值产品列表
export const getProducts = (type) => request.post('/bank/products', null, { params: { type } })

// 发起支付
export const payProduct = (data) => request.post('/bank/payProduct', data)
