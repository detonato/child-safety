FROM node:20-alpine

WORKDIR /app

# نسخ تعريفات المكتبات وتثبيتها
COPY package*.json ./
RUN npm install --production

# نسخ ملفات السيرفر
COPY . .

# المنفذ الخاص بـ Google Cloud Run
ENV PORT=8080
EXPOSE 8080

CMD ["node", "index.js"]
