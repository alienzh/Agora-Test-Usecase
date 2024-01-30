## 开始使用

### 步骤

1. 下载 RTC SDK 和 AIGC SDK （可以直接联系对接的销售或服务获取）, 复制 `*.jar` and `*.aar` 到 [**app/libs**](libs)
   目录, 复制 `*.so` 到 [**app/src/main/jniLibs/${ANDROID_ABI}**](src/main/jniLibs)

2. 放入视频资源文件到 [**app/src/main/assets**](src/main/assets) 目录下，修改 [**MainActivity**](src/main/java/io/agora/sttmediaplayer/MainActivity.kt) 中 `mVideoList` 为你加入的视频文件名 

3. 更新 local.properties. 配置如下:
```mk
#app id
APP_ID=xxxxx
#app certificate
APP_CERTIFICATE=xxxxx
```

3.运行项目
