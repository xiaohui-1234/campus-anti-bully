// 云函数入口文件
const cloud = require('wx-server-sdk')

cloud.init({
  env: cloud.DYNAMIC_CURRENT_ENV
})

const db = cloud.database()

// 云函数入口函数
exports.main = async (event, context) => {
  try {
    const { userInfo } = event
    
    if (!userInfo) {
      return {
        success: false,
        message: '缺少用户信息'
      }
    }
    
    // 获取 openid
    const wxContext = cloud.getWXContext()
    const openid = wxContext.OPENID
    
    console.log('用户 openid:', openid)
    console.log('用户信息:', userInfo)
    
    if (!openid) {
      return {
        success: false,
        message: '获取openid失败'
      }
    }
    
    // 尝试查询用户
    let user
    const nickname = userInfo.nickName || ''
    const avatar = userInfo.avatarUrl || ''
    const currentTime = new Date()
    try {
      const result = await db.collection('login').where({
        openid: openid
      }).get()
      
      console.log('查询用户结果:', result)
      
      if (result.data.length > 0) {
        // 用户存在，更新信息
        user = result.data[0]
        await db.collection('login').doc(user._id).update({
          data: {
            nickname: nickname,
            avatar: avatar,
            updateTime: currentTime
          }
        })
        user = {
          ...user,
          nickname: nickname,
          avatar: avatar,
          updateTime: currentTime
        }
        console.log('更新用户成功')
      } else {
        // 用户不存在，创建
        const addResult = await db.collection('login').add({
          data: {
            openid: openid,
            nickname: nickname,
            avatar: avatar,
            createTime: currentTime,
            updateTime: currentTime
          }
        })
        user = {
          _id: addResult._id,
          openid: openid,
          nickname: nickname,
          avatar: avatar,
          createTime: currentTime,
          updateTime: currentTime
        }
        console.log('创建用户成功:', addResult)
      }
      
      return {
        success: true,
        user: user
      }
    } catch (dbError) {
      console.error('数据库操作失败:', dbError)
      
      // 直接返回错误信息
      return {
        success: false,
        message: '数据库操作失败',
        error: dbError.message,
        errCode: dbError.errCode
      }
    }
  } catch (error) {
    console.error('登录失败:', error)
    return {
      success: false,
      message: '登录失败',
      error: error.message
    }
  }
}