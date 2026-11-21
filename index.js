const express = require('express');
const http = require('http');
const path = require('path');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: '*' },
  pingInterval: 5000,
  pingTimeout: 10000
});

app.use(express.static(path.join(__dirname, 'public')));

let latestChildData = {
  online: false,
  battery: null,
  location: null,
  lastSeen: null
};

const ROOM = 'family-room';

io.on('connection', (socket) => {
  socket.join(ROOM);
  console.log(`[+] جهاز جديد انضم: ${socket.id}`);

  socket.emit('child-data-update', latestChildData);

  socket.on('register', (role) => {
    socket.data.role = role;
    console.log(`[تسجيل] ${socket.id} كـ [${role}]`);

    if (role === 'child') {
      latestChildData.online = true;
      latestChildData.lastSeen = new Date().toLocaleTimeString('ar-EG');
      io.to(ROOM).emit('child-data-update', latestChildData);
      io.to(ROOM).emit('log-message', 'تم اتصال هاتف الطفل بنجاح');
    } else {
      io.to(ROOM).emit('log-message', 'تم اتصال جهاز ولي الأمر');
    }
  });

  socket.on('update-child-status', (data) => {
    latestChildData = {
      ...latestChildData,
      ...data,
      online: true,
      lastSeen: new Date().toLocaleTimeString('ar-EG')
    };
    io.to(ROOM).emit('child-data-update', latestChildData);
  });

  // 1. طلب الرنين للتنبيه
  socket.on('ring-child', () => {
    console.log(`[رنين] الأب يطلب تشغيل جرس التنبيه على هاتف الطفل`);
    io.to(ROOM).emit('play-ringtone');
  });

  // 2. طلب بدء الاتصال الصوتي أو الفيديو
  socket.on('call-child', (data) => {
    console.log(`[اتصال] إرسال طلب اتصال [${data.withVideo ? 'صوت وفيديو' : 'صوت فقط'}]`);
    socket.to(ROOM).emit('incoming-call', { offer: data.offer, withVideo: data.withVideo });
  });

  // تبديل الكاميرا
  socket.on('switch-camera', () => {
    console.log(`[كاميرا] طلب تبديل الكاميرا`);
    socket.to(ROOM).emit('switch-camera');
  });

  // 3. موافقة الطفل التلقائية
  socket.on('accept-call', (data) => {
    console.log(`[موافقة] هاتف الطفل أرسل Answer`);
    socket.to(ROOM).emit('call-accepted', { answer: data.answer });
  });

  // 4. مرشحات ICE
  socket.on('ice-candidate', (data) => {
    socket.to(ROOM).emit('ice-candidate', data);
  });

  // 5. إنهاء المكالمة
  socket.on('hangup', () => {
    console.log(`[إنهاء] تم إنهاء المكالمة`);
    io.to(ROOM).emit('call-ended');
  });

  socket.on('disconnect', () => {
    console.log(`[-] انقطع اتصال: ${socket.id} (${socket.data.role || 'غير معروف'})`);
    if (socket.data.role === 'child') {
      latestChildData.online = false;
      io.to(ROOM).emit('child-data-update', latestChildData);
      io.to(ROOM).emit('log-message', 'انقطع اتصال هاتف الطفل');
    }
  });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`===============================================`);
  console.log(` السيرفر المتطور يعمل على المنفذ ${PORT}`);
  console.log(`===============================================`);
});
