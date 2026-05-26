设备mqtt连接鉴权
说明：分别对应，mqtt服务器地址，Client ID，用户名，密码
{
  "mqtt_url": "mqtt://example.com:1883",
  "device_id": "dev001",
  "product_type": "anti_bullying",
  "device_secret": "xxxxxxxx"
}

/-----------------------------------------------------------------/
发布：device/{product_type}/{device_id}/alarm/post
说明：用于上传报警信息

{
  "mqtt_msg_id": "1779177600123-001-dev001",  
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "event_type": "voice_alarm",
  "alarm_info": "打架",
  "file_name": "001.wav",
  "file_size": 123456,
  "content_type": "audio/wav",
  "timestamp": 1779177600123
}

/-----------------------------------------------------------------/


订阅：device/{product_type}/{device_id}/alarm/upload
说明：返回事件id以及临时上传地址和有效时间

{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "event_id": "evt_xxxx",
  "object_key": "anti_bullying/audio/dev001/2026/05/19/evt_xxxx.wav",
  "upload_url": "https://xxx",
  "expire_seconds": 300
}

/-----------------------------------------------------------------/


发布：device/{product_type}/{device_id}/alarm/confirm
说明：确认上传情况

{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "event_id": "evt_20260519_dev001_xxxx",
  "object_key": "anti_bullying/audio/dev001/2026/05/19/evt_20260519_dev001_xxxx.wav",
  "upload_status": "success"
}

/-----------------------------------------------------------------/


发布：device/{product_type}/{device_id}/status/online
说明：更新在线状态，下线由mqtt发遗嘱，status:offline，timestamp:0

{
  "mqtt_msg_id": "1779177600123-001-dev001"
  "product_type": "anti_bullying",
  "device_id":"dev001",
  "status":"online",
  "timestamp": 1779177600123
}

遗嘱消息主题：
device/{product_type}/{device_id}/status/online

遗嘱消息 QoS：
1

遗嘱消息：
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id": "dev001",
  "status": "offline",
  "timestamp": 0
}


/-----------------------------------------------------------------/

订阅：device/{product_type}/{device_id}/config/wifi/set
说明：wifi修改，修改指定配置号
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "config_id":1,
  "wifi_name":"12345",
  "wifi_password":"123456"
}

/-----------------------------------------------------------------/

发布：device/{product_type}/{device_id}/config/wifi/set/reply
说明：wifi修改，修改指定配置号
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "config_id":1,
  "success":true
}



/-----------------------------------------------------------------/

发布：device/{product_type}/{device_id}/config/wifi/list
说明：返回已有配置
{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "max_size":5,
  "id_using":1,
  "config":[
    {
      "config_id":1,
      "wifi_name":"12345"
    },
    {
      "config_id":2,
      "wifi_name":"12345"
    }
  ]
}

/-----------------------------------------------------------------/

发布：device/{product_type}/{device_id}/bind
说明：返回一个临时的绑定码存缓存用语绑定设备

{
  "mqtt_msg_id": "1779177600123-001-dev001",
  "product_type": "anti_bullying",
  "device_id":"dev001",
  "bind_code":123456
}
