//
//  LiveViewController.swift
//  AudioDualStreamDemo
//
//  Created by FanPengpeng on 2023/6/20.
//

import UIKit
import AgoraRtcKit
import SDWebImage

private let cellID = "userCellID"

class StreamModel: NSObject {
    var hostUid: UInt = 0
    var isDual: Bool = false
    var icon: String = ""
    
    init(hostUid: UInt, isDual: Bool) {
        self.hostUid = hostUid
        self.isDual = isDual
    }
}

class LiveViewController: UIViewController {
    
    @IBOutlet weak var bottomSwitchTtile: UILabel!
    @IBOutlet weak var bottomSwitch: UISwitch!
    @IBOutlet weak var topSwitchContainerView: UIView!
    
    @IBOutlet weak var collectionView: UICollectionView!
    private let rtcManager = RTCManager()
    
    private var modelArray = [StreamModel]()
    
    private let headIconArr = [
        "https://img0.baidu.com/it/u=1993557595,4075530522&fm=253&app=138&size=w931&n=0&f=JPEG&fmt=auto?sec=1687366800&t=98ff991787ccb3c35a1d389f6dabf626",
        "https://img1.baidu.com/it/u=898692534,2766260827&fm=253&app=138&size=w931&n=0&f=JPEG&fmt=auto?sec=1687366800&t=01796ed1d34a5b126fd301ed04dbc86f",
        "https://img1.baidu.com/it/u=3709586903,1286591012&fm=253&app=138&size=w931&n=0&f=JPEG&fmt=auto?sec=1687366800&t=530ed90452e1a0b5851cf702f597c184",
        "https://img0.baidu.com/it/u=1122013262,2429552709&fm=253&app=138&size=w931&n=0&f=JPEG&fmt=auto?sec=1687366800&t=3abab42468346d1c2b6c7e6b990b742a",
        "https://img1.baidu.com/it/u=3709586903,1286591012&fm=253&app=138&size=w931&n=0&f=JPEG&fmt=auto?sec=1687366800&t=530ed90452e1a0b5851cf702f597c184",
        "https://img1.baidu.com/it/u=1889141371,3742825600&fm=253&app=138&size=w931&n=0&f=JPEG&fmt=auto?sec=1687366800&t=297b1e7f8da2dfe0893d59525ea2d810"]
    
    var channelName: String!
    var isAudience: Bool = true
    
    deinit {
        print("=== LiveViewController deinit ===")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        self.title = channelName
        topSwitchContainerView.isHidden = isAudience
        bottomSwitchTtile.text = isAudience ? "Low Stream for All" : "Publish Dual Stream"
        bottomSwitch.isOn = !isAudience
        joinChannel()
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        rtcManager.leave()
    }
    
    private func joinChannel(){
        rtcManager.join(channelName: channelName, uid: KeyCenter.RTC_UID, role: isAudience ? .audience : .broadcaster, delegate: self)
    }
    
    // 点击publish Audio开关
    @IBAction func publishAudioValueDidChange(_ sender: UISwitch) {
        rtcManager.setPublishAudioOn(sender.isOn)
    }
    
    // 底部开关
    @IBAction func bottomSwitchValueDidChange(_ sender: UISwitch) {
        if isAudience {
            modelArray.forEach { model in
                model.isDual = sender.isOn
            }
            rtcManager.setReceiveAllDualStreamOn(sender.isOn)
            collectionView.reloadData()
        }else {
            rtcManager.setPublishDualStreamOn(sender.isOn)
        }
    }
    
    @IBAction func didClickLeaveButton(_ sender: Any) {
        rtcManager.leave()
        navigationController?.popViewController(animated: true)
    }

}


extension LiveViewController: UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell: UserCollectionViewCell = collectionView.dequeueReusableCell(withReuseIdentifier: cellID, for: indexPath) as! UserCollectionViewCell
        let model = modelArray[indexPath.item]
        cell.imgView.backgroundColor = .gray
        cell.imgView.sd_setImage(with: URL(string: model.icon ))
        cell.indicatorLabel.isHidden = !isAudience
        cell.indicatorLabel.text = model.isDual ? "L" : "H"
        cell.nameLabel.text = "\(model.hostUid)"
        
        return cell
    }
    
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return modelArray.count
    }
    
    func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
        return CGSize(width: 100, height: 130)
    }
    
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        if !isAudience { return }
        let highAction = UIAlertAction(title: "High", style: .default) {[weak self] action in
            if let model = self?.modelArray[indexPath.item] {
                model.isDual = false
                self?.rtcManager.setReceiveDualStreamOn(false, uid: model.hostUid)
                self?.collectionView.reloadData()
            }
        }
        let lowAction = UIAlertAction(title: "Low", style: .default) {[weak self] action in
            if let model = self?.modelArray[indexPath.item] {
                model.isDual = true
                self?.rtcManager.setReceiveDualStreamOn(true, uid: model.hostUid)
                self?.collectionView.reloadData()
            }
        }
        
        let cancelAction = UIAlertAction(title: "Cancel", style: .cancel)
        let alertvc = UIAlertController(title: "Select", message: nil, preferredStyle: .actionSheet)
        alertvc.addAction(highAction)
        alertvc.addAction(lowAction)
        alertvc.addAction(cancelAction)
        present(alertvc, animated: true)
    }
}

extension LiveViewController: AgoraRtcEngineDelegate {
    
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinedOfUid uid: UInt, elapsed: Int) {
        print("=====didJoinedOfUid \(uid)")
        if modelArray.filter({$0.hostUid == uid}).isEmpty {
            let model = StreamModel(hostUid: uid, isDual: bottomSwitch.isOn)
            model.icon = headIconArr[modelArray.count % 6]
            modelArray.append(model)
            collectionView.reloadData()
        }
    }
    
    func rtcEngine(_ engine: AgoraRtcEngineKit, didJoinChannel channel: String, withUid uid: UInt, elapsed: Int) {
        if !isAudience {
            print("=====didJoinChannel \(channel)")
            if modelArray.filter({$0.hostUid == uid}).isEmpty {
                let model = StreamModel(hostUid: uid, isDual: bottomSwitch.isOn)
                model.icon = headIconArr[modelArray.count % 6]
                modelArray.append(model)
                collectionView.reloadData()
            }
        }
    }
}
