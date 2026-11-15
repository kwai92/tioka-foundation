# Foundation 管理台与门户镜像（nginx 静态）—— BAS-21 / F-11 §3.2
# 当前为部署占位页；正式单页应用（Vue 3 + Element Plus）由 BAS-FE-* 产出后替换：
#   方案一（推荐）：把 BAS-FE-* 的构建产物拷到本目录，启用下方 `COPY dist/ /usr/share/nginx/html/`；
#   方案二：把 compose 的 build.context 改为仓库根，改 COPY foundation-ui/dist/ /usr/share/nginx/html/。
FROM nginx:1.27-alpine

COPY index.html /usr/share/nginx/html/index.html
# COPY dist/ /usr/share/nginx/html/

EXPOSE 80
