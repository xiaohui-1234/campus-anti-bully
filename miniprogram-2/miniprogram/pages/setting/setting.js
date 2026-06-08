Page({
  data: {
    devicePlace:'',
    deviceName: '',
    deviceId: '',
    config: {
      setinfo_url: 'https://iot-api.heclouds.com/thingmodel/set-device-property',
      authorization: 'version=2018-10-31&res=products%2Fl4JQEioAnm%2Fdevices%2Ftest1&et=1833956099&method=md5&sign=i9er2cmvssPmGPa4miz%2BSA%3D%3D',
      product_id: 'l4JQEioAnm',
      device_name: 'test1'
    }
  },

  onDeviceNameInput(e) {
    this.setData({ deviceName: e.detail.value });
  },

  onDevicePlaceInput(e) {
    this.setData({ devicePlace: e.detail.value });
  },

  onDeviceIdInput(e) {
    this.setData({ deviceId: e.detail.value });
  },

  submitForm() {
    if (!this.data.deviceName.trim()) {
      wx.showToast({ title: '设备名称不能为空', icon: 'none' });
      return;
    }
    if (!this.data.deviceId.trim()){
      wx.showToast({title: '产品ID不能为空',icon: 'none'});
      return;
    }
    if (!this.data.devicePlace.trim()){
      wx.showToast({title: '设备地点不能为空',icon: 'none'});
      return;
    }
    const mockEvent = { detail: { value: this.data.devicePlace } };
    this.Onenet_SetName(mockEvent);
  },

  Onenet_SetName(event) {
    const { config } = this.data;
    const newDevicePlace = event.detail.value;

    if (!config) {
      wx.showToast({ title: '配置信息未初始化', icon: 'none' });
      console.error('config未定义');
      return;
    }

    wx.showLoading({ title: '正在修改...' });

    wx.request({
      url: "https://iot-api.heclouds.com/thingmodel/set-device-property",
      header: {
        'authorization': config.authorization,
        'Content-Type': 'application/json' 
      },
      method: "POST",
      
      data: {
        "product_id": "l4JQEioAnm", //替换deviceId
        "device_name": "test1", //替换deviceName
        "params": {
          // deviceName需和OneNET定义的属性标识符完全一致
          "deviceName": newDevicePlace
        }
      },
      complete: (res) => {
  wx.hideLoading();
  console.log('OneNET接口返回结果：', res);

  // 先判断是否有响应数据
  if (!res || !res.data) {
    wx.showToast({ title: '网络请求失败或接口无响应', icon: 'none' });
    return;
  }

  // 解构前先确保字段存在
  const { errno, error } = res.data;

  // 业务成功
  wx.showToast({ title: '修改成功', icon: 'success' });
  this.setData({ 
    deviceName: '',
    deviceId: '',
    devicePlace:''
  });
  }
    });
  },

  onLoad(options) {}
});