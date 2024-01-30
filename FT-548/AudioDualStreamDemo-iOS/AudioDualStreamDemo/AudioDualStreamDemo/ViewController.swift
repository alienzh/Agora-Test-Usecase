//
//  ViewController.swift
//  AudioDualStreamDemo
//
//  Created by FanPengpeng on 2023/6/16.
//

import UIKit

private let kJoinSegueID = "joinChannel"

class ViewController: UIViewController {
    
    @IBOutlet weak var channelTF: UITextField!
    @IBOutlet weak var segmentCtrl: UISegmentedControl!

    override func viewDidLoad() {
        super.viewDidLoad()
    }
    
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        view.endEditing(true)
    }

    // MARK: - Navigation
    
    override func shouldPerformSegue(withIdentifier identifier: String, sender: Any?) -> Bool {
        if identifier == kJoinSegueID {
            guard let channel = channelTF.text?.trimmingCharacters(in: .whitespacesAndNewlines), channel.count > 0 else {
                print("频道名称不能为空")
                return false
            }
        }
        return true
    }

    // In a storyboard-based application, you will often want to do a little preparation before navigation
    override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
        if segue.identifier == kJoinSegueID {
            guard let channelName = channelTF.text?.trimmingCharacters(in: .whitespacesAndNewlines) else {
                return
            }
            let roomVC = segue.destination as! LiveViewController
            roomVC.channelName = channelName
            roomVC.isAudience = segmentCtrl.selectedSegmentIndex == 0
        }
    }


}

