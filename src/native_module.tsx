import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-usb-serialport-for-android' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

export interface Device {
  readonly deviceId: number;
  readonly vendorId: number;
  readonly productId: number;
}

interface UsbSerialportForAndroidAPI {
  list(): Promise<Device[]>;
  // return 1 if already has permission, 0 will request permission
  tryRequestPermission(deviceId: number): Promise<number>;
  hasPermission(deviceId: number): Promise<boolean>;
  open(
    deviceId: number,
    baudRate: number,
    dataBits: number,
    stopBits: number,
    parity: number
  ): Promise<any>;
  send(deviceId: number, hexStr: string): Promise<null>;
  writeToUsbPort(deviceId: number, base64: string): Promise<any>;
  sendCommand(command: string): Promise<any>;
  close(deviceId: number): Promise<null>;
  startListening(vendorId: number, productId: number): any;
}

const UsbSerialportForAndroid: UsbSerialportForAndroidAPI =
  NativeModules.UsbSerialportForAndroid
    ? NativeModules.UsbSerialportForAndroid
    : new Proxy(
        {},
        {
          get() {
            throw new Error(LINKING_ERROR);
          },
        }
      );

export default UsbSerialportForAndroid;
