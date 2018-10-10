import Flutter
import UIKit
import CoreBluetooth

public class SwiftFrccbluePlugin: NSObject, FlutterPlugin, CBPeripheralManagerDelegate {
    
    var peripheralManager:CBPeripheralManager?
    var c:CBCentralManager?
    var channel:FlutterMethodChannel?
    
    let centralDic:NSMutableDictionary = [:]
    let characteristicDic:NSMutableDictionary = [:]
    
    let NOTIFY_MTU = 20
    var sendDataIndex = 0
    
    var dataToSend:Data?
    var characteristicToSend:CBMutableCharacteristic?
    var centralToSend:CBCentral?
    
    public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "frccblue", binaryMessenger: registrar.messenger())
    let instance = SwiftFrccbluePlugin()
        instance.channel = channel
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    private var Service_UUID: String = "00000000-0000-0000-0000-AAAAAAAAAAA1"
    private var Characteristic_UUID: String = "00000000-0000-0000-0000-AAAAAAAAAAA2"

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        if call.method == "getPlatformVersion"{
            result("iOS sss" + UIDevice.current.systemVersion)
        }
        if call.method == "startPeripheral" {
            print("startPeripheral")
            let param = call.arguments as! Dictionary<String,String>
            Service_UUID = param["serviceUUID"]!
            Characteristic_UUID = param["characteristicUUID"]!
            peripheralManager = CBPeripheralManager.init(delegate: self, queue: .main)
        }
        if call.method == "stopPeripheral" {
            print("stopPeripheral")
            peripheralManager?.stopAdvertising()
        }
        if call.method == "peripheralUpdateValue" {
            let param = call.arguments as! NSDictionary
            let centraluuidString = (param["centraluuidString"] as? NSString) ?? ""
            let characteristicuuidString = (param["characteristicuuidString"] as? NSString) ?? ""
            let data = (param["data"] as? FlutterStandardTypedData) ?? FlutterStandardTypedData()
            self.dataToSend = data.data
            self.characteristicToSend = (characteristicDic[characteristicuuidString]) as? CBMutableCharacteristic
            self.centralToSend = centralDic[centraluuidString] as? CBCentral
            self.sendData(data: self.dataToSend! as NSData, characteristic: self.characteristicToSend!, central: self.centralToSend!)
        }
    }
    
    func sendData(data:NSData, characteristic:CBMutableCharacteristic, central:CBCentral){
        var didSend = true;
        while (didSend) {
            var amountToSend = data.length - self.sendDataIndex
            if amountToSend > NOTIFY_MTU {
                amountToSend = NOTIFY_MTU;
            }
            let chunk = NSData(bytes: data.bytes+self.sendDataIndex, length: amountToSend)
            didSend = peripheralManager?.updateValue(chunk as Data, for: characteristic, onSubscribedCentrals: [central]) ?? false
            if !didSend {
                return;
            }
            self.sendDataIndex += amountToSend
            if self.sendDataIndex >= data.length {
                peripheralManager?.updateValue("EOF".data(using: String.Encoding.utf8) ?? Data(), for: characteristic, onSubscribedCentrals: [central])
                return
            }
        }
    }
    
    public func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        var state = "unknown"
        switch peripheral.state {
        case .unknown:
            print("未知的")
            state = "unknown"
        case .resetting:
            print("重置中")
            state = "resetting"
        case .unsupported:
            print("不支持")
            state = "unsupported"
        case .unauthorized:
            print("未验证")
            state = "unauthorized"
        case .poweredOff:
            print("未启动")
            state = "poweredOff"
            self.peripheralManager?.stopAdvertising()
        case .poweredOn:
            print("可用")
            state = "poweredOn"
            setupServiceAndCharacteristics()
            self.peripheralManager?.startAdvertising([CBAdvertisementDataServiceUUIDsKey : [CBUUID.init(string: Service_UUID)]])
        }
        channel?.invokeMethod("peripheralManagerDidUpdateState", arguments: state)
    }
    
    private func setupServiceAndCharacteristics() {
        let serviceID = CBUUID.init(string: Service_UUID)
        let service = CBMutableService.init(type: serviceID, primary: true)
        let characteristicID = CBUUID.init(string: Characteristic_UUID)
        let characteristic = CBMutableCharacteristic.init(type: characteristicID,
                                                          properties: [.read, .write, .notify],
                                                          value: nil,
                                                          permissions: [.readable, .writeable])
        service.characteristics = [characteristic]
        self.peripheralManager?.add(service)
    }
    
    public func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didSubscribeTo characteristic: CBCharacteristic) {
        print("didSubscribeTo"+central.identifier.uuidString)
        centralDic[central.identifier.uuidString] = central
        characteristicDic[characteristic.uuid.uuidString] = characteristic
        
        channel?.invokeMethod("didSubscribeTo", arguments: ["centraluuidString":central.identifier.uuidString,"characteristicuuidString":characteristic.uuid.uuidString])
    }
    
    public func peripheralManager(_ peripheral: CBPeripheralManager, central: CBCentral, didUnsubscribeFrom characteristic: CBCharacteristic) {
        print("didUnsubscribeFrom"+central.identifier.uuidString)
        centralDic[central.identifier.uuidString] = nil
        characteristicDic[characteristic.uuid.uuidString] = nil
        channel?.invokeMethod("didUnsubscribeFrom", arguments: ["centraluuidString":central.identifier.uuidString,"characteristicuuidString":characteristic.uuid.uuidString])
    }
    
    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        channel?.invokeMethod("didReceiveRead", arguments: ["centraluuidString":request.central.identifier.uuidString,"characteristicuuidString":request.characteristic.uuid.uuidString], result: { (data) in
            if let da = data as? FlutterStandardTypedData {
                request.value = da.data
                peripheral.respond(to: request, withResult: .success)
            }
        })
    }
    
    public func peripheralManagerIsReady(toUpdateSubscribers peripheral: CBPeripheralManager) {
        self.sendData(data: self.dataToSend! as NSData, characteristic: self.characteristicToSend!, central: self.centralToSend!)
    }
    
    public func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        for request in requests {
            if let data = request.value {
                channel?.invokeMethod("didReceiveWrite", arguments: ["centraluuidString":request.central.identifier.uuidString,"characteristicuuidString":request.characteristic.uuid.uuidString,"data":FlutterStandardTypedData.init(bytes: data)])
            }
            request.value = nil
            peripheral.respond(to: request, withResult: .success)
        }
    }
}
