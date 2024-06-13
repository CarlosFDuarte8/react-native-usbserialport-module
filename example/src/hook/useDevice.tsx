import React from 'react';
import {
  type DeviceType,
  Parity,
  type UsbSerial,
  UsbSerialManager,
} from 'react-native-usbserialport-module';

export type DeviceUSBType = DeviceType;

const useDevice = () => {
  const [devicesFound, setDevicesFound] = React.useState<DeviceUSBType[]>([]);
  const [error, setError] = React.useState<string>('');
  const [deviceConnectedUSB, setDeviceConnectedUSB] = React.useState(false);
  const [multiscentUSB, setMultiscentUSB] =
    React.useState<DeviceUSBType | null>(null);
  const [multiscentConnected, setMultiscentConnected] =
    React.useState<UsbSerial | null>(null);
  const [statusMessage, setStatusMessage] = React.useState<string>('');

  const onScanAndListUsbDevices = async () => {
    try {
      const devices = await UsbSerialManager.list();
      setStatusMessage('');
      setDevicesFound(devices);
    } catch (e) {
      setError('Erro ao buscar dispositivos USB.');
      console.error(e);
    }
  };

  React.useEffect(() => {
    onScanAndListUsbDevices();
  }, []);

  const onConnectDevice = async (device: DeviceUSBType) => {
    try {
      await UsbSerialManager.tryRequestPermission(device.deviceId);

      const permission = await UsbSerialManager.hasPermission(device.deviceId);
      if (!permission) {
        throw new Error('No permission to access the device');
      }

      const options = {
        baudRate: 115200,
        parity: Parity.None,
        dataBits: 8,
        stopBits: 1,
      };

      const usbSerialPort = await UsbSerialManager.open(
        device.deviceId,
        options
      );
      setMultiscentUSB(device);
      setMultiscentConnected(usbSerialPort);
      await usbSerialPort.sendCommand(`multiscent cmd 0`);
      await usbSerialPort.monitor(
        (data) => {
          const message = hexToString(data.data);
          setStatusMessage(message);
        },
        (err) => {
          console.error('Error occurred:', err);
          onDisconnectDevice();
        }
      );
      setDeviceConnectedUSB(true);
    } catch (err) {
      console.error('Error in handleDeviceConnect:', err);
    }
  };

  const onDisconnectDevice = async () => {
    try {
      await multiscentConnected?.close();
      setDeviceConnectedUSB(false);
      onScanAndListUsbDevices();
    } catch (err) {
      console.error('Error during disconnect:', err);
    }
  };

  const onSendCommand = async (command: string) => {
    try {
      if (!multiscentConnected) {
        throw new Error('No connected device');
      }
      await multiscentConnected.sendCommand(`multiscent cmd ${command}`);

      await multiscentConnected.monitor(
        (data) => {
          const message = hexToString(data.data);
          setStatusMessage(message);
        },
        (err) => {
          console.error('Error occurred:', err);
          onDisconnectDevice();
        }
      );
    } catch (err) {
      console.error('Error sending command:', err);
    }
  };

  const hexToString = (hex: string): string => {
    let str = '';
    for (let i = 0; i < hex.length; i += 2) {
      str += String.fromCharCode(parseInt(hex.substr(i, 2), 16));
    }
    return str;
  };

  return {
    deviceConnectedUSB,
    devicesFound,
    error,
    multiscentUSB,
    statusMessage,
    onConnectDevice,
    onDisconnectDevice,
    onScanAndListUsbDevices,
    onSendCommand,
  };
};

export { useDevice as useDeviceUSBPort };
