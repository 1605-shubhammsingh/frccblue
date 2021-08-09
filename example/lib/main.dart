import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:frccblue/frccblue.dart';

void main() => runApp(new MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String readSentArguments = 'Nothing';
  String writeReceivedArguments = 'Nothing';

  final String serviceUUID = "00000000-0000-0000-0000-AAAAAAAAAAA1";
  final String readCharacteristicsUUID = "000029d1-0000-0000-0000-AAAAAAAAAAA2";
  final String writeCharacteristicsUUID =
      "000029d2-0000-0000-0000-AAAAAAAAAAA3";

  @override
  void initState() {
    super.initState();
  }

  Future<void> initPlatformState() async {
    Frccblue.init(didReceiveRead: (MethodCall call) {
      print(call.arguments);
      setState(() {
        readSentArguments = call.arguments.toString();
      });
      return Uint8List.fromList([
        11,
        2,
        3,
        4,
        5,
        6,
        7,
        8,
        9,
        10,
      ]);
    }, didReceiveWrite: (MethodCall call) {
      Frccblue.peripheralUpdateValue(
          call.arguments["centraluuidString"],
          call.arguments["characteristicuuidString"],
          Uint8List.fromList([11, 2, 3]),
          false);
      print(call.arguments);
      try {
        print(call.arguments["data"].toString());
        setState(() {
          writeReceivedArguments = String.fromCharCodes(call.arguments["data"]);
        });
      } catch (e) {
        print(e.toString());
      }
    }, didSubscribeTo: (MethodCall call) {
      print(call.arguments);
      Frccblue.peripheralUpdateValue(
          call.arguments["centraluuidString"],
          call.arguments["characteristicuuidString"],
          Uint8List.fromList([11, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 2, 3]),
          true);
    }, didUnsubscribeFrom: (MethodCall call) {
      print(call.arguments);
    }, peripheralManagerDidUpdateState: (MethodCall call) {
      print(call.arguments);
    });

    Frccblue.startPeripheral(
            serviceUUID, readCharacteristicsUUID, writeCharacteristicsUUID)
        .then((_) {});

    if (!mounted) return;
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new Scaffold(
        appBar: new AppBar(
          title: const Text('Plugin example app'),
        ),
        body: new Center(
          child: Column(
            children: [
              const SizedBox(height: 20),
              new Text('Read sent arguments: $readSentArguments'),
              const SizedBox(height: 20),
              new Text('Write received arguments: $writeReceivedArguments'),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                children: [
                  ElevatedButton(
                    onPressed: () {
                      initPlatformState();
                    },
                    child: Text("Start"),
                  ),
                  ElevatedButton(
                    onPressed: () {
                      Frccblue.stopPeripheral();
                    },
                    child: Text("Stop"),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
