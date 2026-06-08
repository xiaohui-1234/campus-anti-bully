Page({
  data: {
    deviceName: '',
    time: '',
    eventType: '',
    desc: '',
    lastAudioTime: '',
    cardList: []
  },

  config: {
    authorization: "version=2018-10-31&res=products%2Fl4JQEioAnm%2Fdevices%2Ftest1&et=1833956099&method=md5&sign=i9er2cmvssPmGPa4miz%2BSA%3D%3D",
    product_id: "l4JQEioAnm",
    device_name: "test1",
    getinfo_url: "https://iot-api.heclouds.com/thingmodel/query-device-property?product_id=l4JQEioAnm&device_name=test1",
    setinfo_url: 'https://iot-api.heclouds.com/thingmodel/set-device-property'
  },

  getValueByIdentifier(dataList, identifier) {
    if (!dataList || dataList.length === 0) return '';
    const item = dataList.find(i => i.identifier === identifier);
    if (!item) return '';
    if (identifier === 'time') {
      return item.time || '';
    }
    return item.value || '';
  },

  decodeUnicode(str) {
    if (!str || typeof str !== 'string') return str || '';
    return str.replace(/u([0-9a-fA-F]{4})/g, (match, hex) => {
      return String.fromCharCode(parseInt(hex, 16));
    });
  },

  formatTimestamp(timestamp) {
    if (!timestamp) return '未知时间';
    let ts = Number(timestamp);
    if (String(ts).length === 10) ts *= 1000;
    const date = new Date(ts);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  },

  initLastTimeFromDB() {
    const db = wx.cloud.database();
    db.collection('message').orderBy('createTime', 'desc').limit(1).get({
      success: res => {
        if (res.data && res.data.length > 0) {
          const latestAudioTime = res.data[0].audioOriginTime;
          console.log("✅ 数据库最新录音时间:", latestAudioTime);
          this.setData({ lastAudioTime: latestAudioTime })
        } else {
          console.log("✅ 数据库无数据，首次运行");
        }
      },
      fail: err => console.error("加载失败", err)
    })
  },

  // ======================= 核心修复 =======================
  onenetGetInfo() {
    wx.request({
      url: this.config.getinfo_url,
      header: { authorization: this.config.authorization },
      success: res => {
        console.log("📡 设备数据:", res.data);
        const dataList = res.data?.data || [];

        const timeValue = this.getValueByIdentifier(dataList, 'time');
        const descValue = this.decodeUnicode(this.getValueByIdentifier(dataList, 'desc'));
        const eventTypeValue = this.decodeUnicode(this.getValueByIdentifier(dataList, 'eventType'));
        const deviceNameValue = this.decodeUnicode(this.getValueByIdentifier(dataList, 'deviceName'));
        const audioUrlValue = this.getValueByIdentifier(dataList, 'audio_url');
        const formattedTime = this.formatTimestamp(timeValue);

        // 取出 audio_url 自己的时间戳
        const audioUrlItem = dataList.find(item => item.identifier === 'audio_url');
        const currentAudioTime = audioUrlItem?.time || '';
        const lastAudioTime = this.data.lastAudioTime;

        console.log("⏱ 上次录音时间:", lastAudioTime);
        console.log("⏱ 当前录音时间:", currentAudioTime);

        // 关键：时间不一样 → 才存库
        const needSave = lastAudioTime !== currentAudioTime && currentAudioTime;

        if (needSave) {
          console.log("✅ 新录音 → 保存数据库");

          this.saveDataToDatabase(
            deviceNameValue,
            formattedTime,
            eventTypeValue,
            descValue,
            audioUrlValue,
            currentAudioTime  // 把时间戳一起传过去！
          );

          this.setData({ lastAudioTime: currentAudioTime });
        }

        this.setData({
          deviceName: deviceNameValue || '未知设备',
          time: formattedTime,
          eventType: eventTypeValue || '正常',
          desc: descValue || '无描述',
        });
      },
      complete: () => wx.stopPullDownRefresh()
    });
  },

  // ======================= 存库函数修复 =======================
  saveDataToDatabase(deviceName, time, eventType, desc, audioUrl, audioOriginTime) {
    const db = wx.cloud.database();
    db.collection('message').add({
      data: {
        deviceName,
        time,
        eventType,
        desc,
        audioUrl,
        audioOriginTime,  // 必须存！
        createTime: db.serverDate()
      },
      success: () => {
        console.log("💾 保存成功！");
        this.loadDataFromDatabase();
      },
      fail: (err) => {
        console.error("❌ 保存失败", err);
      }
    })
  },

  onenetSetName(e) {
    wx.request({
      url: this.config.setinfo_url,
      header: { authorization: this.config.authorization },
      method: 'POST',
      data: {
        product_id: this.config.product_id,
        device_name: this.config.device_name,
        params: { deviceName: e.detail.value }
      },
      success: () => wx.showToast({ title: '成功' })
    });
  },

  loadDataFromDatabase() {
    const db = wx.cloud.database();
    db.collection('message').orderBy('createTime', 'desc').limit(100).get({
      success: res => {
        this.setData({
          cardList: res.data.map(item => ({
            ...item, eventTypeClass: "error"
          }))
        })
      }
    })
  },

  onLoad() {
    this.loadDataFromDatabase();
    this.initLastTimeFromDB();

    setTimeout(() => {
      this.onenetGetInfo();
      this.timer = setInterval(() => this.onenetGetInfo(), 2000);
    }, 1000);
  },

  onPullDownRefresh() {
    this.onenetGetInfo();
  },

  handlePlayback(e) {
    const id = e.currentTarget.dataset.id;
    const card = this.data.cardList.find(item => item._id === id);

    if (!card || !card.audioUrl) {
      wx.showToast({ title: '暂无录音', icon: 'none' });
      return;
    }

    if (!this.audioContext) {
      this.audioContext = wx.createInnerAudioContext();
    }

    const audioContext = this.audioContext;
    audioContext.src = card.audioUrl;

    audioContext.onError((err) => {
      console.error('播放失败', err);
      wx.showToast({ title: '播放失败', icon: 'none' });
    });

    audioContext.onEnded(() => {
      console.log('播放结束');
    });

    audioContext.play();
    wx.showToast({ title: '正在播放', icon: 'none', duration: 1500 });
  },

  onUnload() {
    clearInterval(this.timer);
    if (this.audioContext) {
      this.audioContext.destroy();
    }
  }
})