# frccblue

A new Flutter plugin.

## Getting Started

For help getting started with Flutter, view our online
[documentation](https://flutter.io/).

For help on editing plugin code, view the [documentation](https://flutter.io/developing-packages/#edit-plugin-package).

## Supports
 Android

# Installing
```
dependencies:
  frccblue:
    git:
      url: https://github.com/1605-shubhammsingh/frccblue
```
      
      
# Initialization
```
Frccblue.init(didReceiveRead:(MethodCall call){
      print(call.arguments);
      return Uint8List.fromList([11,2,3,4,5,6,7,8,9,10,]);
    }, didReceiveWrite:(MethodCall call){
      print(call.arguments);
    },didSubscribeTo: (MethodCall call){
      print(call.arguments);
//      Frccblue.peripheralUpdateValue()
    },didUnsubscribeFrom: (MethodCall call){
      print(call.arguments);
    },peripheralManagerDidUpdateState: (MethodCall call){
      print(call.arguments);
    });

Here we need to specify different characteristics for read and write however earlier it was single characteristics only
Frccblue.startPeripheral(serviceUUID, readCharacteristicsUUID, writeCharacteristicsUUID).then((_){});
```

# more
## peripheralManagerDidUpdateState
iOS
```
switch peripheral.state {
        case .unknown:
            print("unknown")
            state = "unknown"
        case .resetting:
            print("resetting")
            state = "resetting"
        case .unsupported:
            print("unsupported")
            state = "unsupported"
        case .unauthorized:
            print("unauthorized")
            state = "unauthorized"
        case .poweredOff:
            print("poweredOff")
            state = "poweredOff"
            self.peripheralManager?.stopAdvertising()
        case .poweredOn:
            print("poweredOn")
            state = "poweredOn"
```
android
```
"unknown"
"poweredOff"
"poweredOn"
```
