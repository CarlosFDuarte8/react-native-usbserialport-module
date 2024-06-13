import * as React from 'react';
import {
  Dimensions,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
  type ListRenderItem,
} from 'react-native';
import { useDeviceUSBPort, type DeviceUSBType } from './hook/useDevice';

const { fontScale, height, width } = Dimensions.get('window');
export default function App() {
  const {
    deviceConnectedUSB,
    devicesFound,
    error,
    multiscentUSB,
    onConnectDevice,
    onDisconnectDevice,
    onScanAndListUsbDevices,
    onSendCommand,
    statusMessage,
  } = useDeviceUSBPort();

  const renderItem: ListRenderItem<DeviceUSBType> = ({ item, index }) => (
    <View key={index} style={styles.card}>
      <View style={styles.cardContent}>
        <View style={styles.deviceDetails}>
          <View>
            <Text style={styles.deviceName}>{item.name}</Text>
            <Text style={styles.deviceMac}>{item.deviceId}</Text>
          </View>
          <TouchableOpacity
            style={styles.connectButton}
            onPress={() => {
              if (deviceConnectedUSB) {
                onDisconnectDevice();
                return;
              }
              onConnectDevice(item);
            }}
          >
            <Text style={styles.connectText}>
              {deviceConnectedUSB ? 'Desconectar' : 'Conectar'}
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    </View>
  );

  return (
    <View style={styles.container}>
      <Text style={styles.header}>Conexão usb</Text>
      <TouchableOpacity
        style={styles.commandButton}
        onPress={onScanAndListUsbDevices}
      >
        <Text style={styles.text}>Procurar Dispositivos</Text>
      </TouchableOpacity>

      <FlatList
        data={devicesFound}
        renderItem={renderItem}
        keyExtractor={(item) => item.productId.toString()}
        style={styles.list}
        ListEmptyComponent={<Text>Nenhum dispositivo encontrado</Text>}
      />
      {deviceConnectedUSB && (
        <View style={styles.connectedButtonsContainer}>
          <TouchableOpacity
            style={styles.commandButton}
            onPress={() => onSendCommand('0')}
          >
            <Text style={styles.text}>Calibrar {multiscentUSB?.name}</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={styles.commandButton}
            onPress={() => onSendCommand('3 5')}
          >
            <Text style={styles.text}>
              Recarregar posição 5 {multiscentUSB?.name}
            </Text>
          </TouchableOpacity>
        </View>
      )}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {statusMessage ? <Text style={styles.error}>{statusMessage}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 12,
    backgroundColor: '#fff',
    borderRadius: 8,
    margin: 16,
  },
  header: {
    marginBottom: 16,
    textAlign: 'center',
    fontSize: 20,
    textTransform: 'uppercase',
  },
  divider: {
    marginVertical: 16,
  },
  list: {
    marginVertical: 6,
  },
  card: {
    margin: 16,
    backgroundColor: '#fff',
    elevation: 8,
    padding: 16,
    borderRadius: 8,
  },
  cardContent: {
    flexDirection: 'row',
  },
  image: {
    height: width * 0.2,
    minWidth: '20%',
  },
  deviceDetails: {
    flex: 1,
    justifyContent: 'space-between',
  },
  deviceName: {
    fontSize: fontScale * 25,
    fontWeight: 'bold',
  },
  deviceMac: {
    fontSize: fontScale * 20,
    fontWeight: 'bold',
  },
  connectedButtonsContainer: {
    margin: 8,
  },
  commandButton: {
    marginTop: 5,
  },
  error: {
    color: 'red',
    marginTop: 16,
  },
  connectButton: {
    width: '100%',
    backgroundColor: '#1204d3',
    height: height / 25,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 8,
    marginTop: 16,
  },
  connectText: {
    textTransform: 'uppercase',
    fontSize: fontScale * 25,
    color: '#fff',
    fontWeight: 'bold',
  },
  text: {
    textTransform: 'uppercase',
    fontSize: fontScale * 20,
    color: '#fff',
    fontWeight: 'bold',
  },
});
