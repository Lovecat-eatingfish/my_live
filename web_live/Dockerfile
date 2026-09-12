# 旗鱼直播前端镜像（构建上下文：web_live/，需先 npm run build 生成 dist/）
FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/
EXPOSE 80
