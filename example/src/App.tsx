import * as React from 'react';
import { StyleSheet, View, Text, Button, FlatList, Alert } from 'react-native';
import {
  UsbSerialManager,
  Device,
  Parity,
  OpenOptions,
} from 'react-native-usbserialport-module-android';

export default function App() {
  const [devices, setDevices] = React.useState<Device[]>([]);
  const [selectedDevice, setSelectedDevice] = React.useState<Device | null>(
    null
  );
  const [openPort, setOpenPort] = React.useState<any>(null);
  const [receivedData, setReceivedData] = React.useState<string>('');

  React.useEffect(() => {
    // List USB devices on component mount
    UsbSerialManager.list()
      .then(setDevices)
      .catch((error) => Alert.alert('Error', error.message));
  }, []);

  const handleOpenPort = async (device: Device) => {
    try {
      const hasPermission = await UsbSerialManager.tryRequestPermission(
        device.deviceId
      );
      if (!hasPermission) {
        Alert.alert('Permission denied');
        return;
      }

      const options: OpenOptions = {
        baudRate: 9600,
        dataBits: 8,
        stopBits: 1,
        parity: Parity.None,
      };

      const port = await UsbSerialManager.open(device.deviceId, options);
      setOpenPort(port);
      setSelectedDevice(device);
    } catch (error) {
      Alert.alert('Error', error.message);
    }
  };

  const handleClosePort = async () => {
    if (openPort) {
      await openPort.close();
      setOpenPort(null);
      setSelectedDevice(null);
    }
  };

  const handleSendData = async (data: string) => {
    if (openPort) {
      try {
        const hexStr = Buffer.from(data, 'utf-8').toString('hex');
        await openPort.send(hexStr);
      } catch (error) {
        Alert.alert('Error', error.message);
      }
    }
  };

  const handleDataReceived = (event: any) => {
    setReceivedData(event.data);
  };

  React.useEffect(() => {
    if (openPort) {
      const subscription = openPort.on(
        'usbSerialPortDataReceived',
        handleDataReceived
      );
      return () => {
        subscription.remove();
      };
    }
  }, [openPort]);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>USB Devices:</Text>
      <FlatList
        data={devices}
        keyExtractor={(item) => item.deviceId.toString()}
        renderItem={({ item }) => (
          <View style={styles.deviceItem}>
            <Text>{item.deviceName}</Text>
            <Button
              title="Open Port"
              onPress={() => handleOpenPort(item)}
              disabled={selectedDevice !== null}
            />
          </View>
        )}
      />
      {selectedDevice && (
        <View style={styles.portInfo}>
          <Text>Opened Port on Device: {selectedDevice.deviceName}</Text>
          <Button title="Close Port" onPress={handleClosePort} />
          <Button
            title="Send Data"
            onPress={() => handleSendData('Hello, USB!')}
          />
          <Text>Received Data: {receivedData}</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 16,
  },
  deviceItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#ccc',
  },
  portInfo: {
    marginTop: 16,
    padding: 16,
    backgroundColor: '#f9f9f9',
    borderRadius: 8,
  },
});
