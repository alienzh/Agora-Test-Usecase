//
//  RTCManager.swift
//  Game2035OCNew
//
//  Created by FanPengpeng on 2023/4/15.
//

import UIKit
import AgoraRtcKit

class RTCManager: NSObject {
    
    private var agoraKit: AgoraRtcEngineKit!
    @objc var roomId: String?
    private var streamId: Int = 0
    
    private var connectionEx = AgoraRtcConnection()
//    private var connection = AgoraRtcConnection()
    
    private func createEngine(){
        let config = AgoraRtcEngineConfig()
        config.appId = KeyCenter.AppId
        
        let agoraKit = AgoraRtcEngineKit.sharedEngine(with: config, delegate: nil)
        self.agoraKit = agoraKit
        // get channel name from configs
//        agoraKit.setChannelProfile(.liveBroadcasting)
        agoraKit.setAudioProfile(.musicHighQualityStereo, scenario: .gameStreaming)
        agoraKit.enableAudio()
        agoraKit.setDefaultAudioRouteToSpeakerphone(true)
    }

    private func joinChannel(channelName: String, uid: UInt) {
        let option = AgoraRtcChannelMediaOptions()
        option.autoSubscribeAudio = true
        let ret = agoraKit.joinChannel(byToken:nil, channelId: channelName, info: nil, uid: uid)
        if ret != 0 {
            print("joinChannel call failed: \(ret), please check your params")
        }
    }
    
    private func joinChannelEx(channelName: String, uid: UInt, role: AgoraClientRole, delegate: AgoraRtcEngineDelegate) {
        
        let option = AgoraRtcChannelMediaOptions()
        option.autoSubscribeAudio = true
        option.clientRoleType = role
        option.publishMicrophoneTrack = role == .broadcaster
        connectionEx.localUid = uid
        connectionEx.channelId = channelName + "_low"
        agoraKit.joinChannelEx(byToken: nil, connection: connectionEx, delegate: delegate, mediaOptions: option)
    }
    
    /// make myself a broadcaster
   private func becomeBroadcaster() {
       agoraKit.enableLocalAudio(true)
       agoraKit.setClientRole(.broadcaster)
    }
    
    /// make myself an audience
    private func becomeAudience() {
        agoraKit.setClientRole(.audience)
    }

}

extension RTCManager {
    
    /// 加入频道
    /// - Parameters:
    ///   - channelName: 频道名称
    ///   - uid: 用户id
    ///   - videoView: 不需要视频功能此处传nil
    ///   - delegate:
    func join(channelName:String, uid: UInt, role: AgoraClientRole, delegate: AgoraRtcEngineDelegate){
        createEngine()
        if role == .audience {
            becomeAudience()
        }else{
            becomeBroadcaster()
        }
        joinChannel(channelName: channelName, uid: uid)
        agoraKit.delegate = delegate
        joinChannelEx(channelName: channelName, uid: uid, role: role, delegate: delegate)
        
        setReceiveAllDualStreamOn(false)
    }
    
    // 推流开启/关闭大流
    func setPublishAudioOn(_ isOn: Bool) {
        agoraKit.muteLocalAudioStream(!isOn)
    }
    
    // 推流开启/关闭小流
    func setPublishDualStreamOn(_ isOn: Bool) {
        agoraKit.muteLocalAudioStreamEx(!isOn, connection: connectionEx)
    }
    
    /// 接收小流开关
    /// - Parameters:
    ///   - isOn: 开启/关闭
    ///   - channel: channel / _ex
    func setReceiveDualStreamOn(_ isOn: Bool, uid: UInt) {
        agoraKit.muteRemoteAudioStream(uid, mute: isOn)
        agoraKit.muteRemoteAudioStreamEx(uid, mute: !isOn, connection: connectionEx)
    }
    
    func setReceiveAllDualStreamOn(_ isOn: Bool) {
        agoraKit.muteAllRemoteAudioStreams(isOn)
        agoraKit.muteAllRemoteAudioStreamsEx(!isOn, connection: connectionEx)
    }
    
    /// 离开频道
    func leave(){
        agoraKit.leaveChannel()
        agoraKit.leaveChannelEx(connectionEx)
    }
}


