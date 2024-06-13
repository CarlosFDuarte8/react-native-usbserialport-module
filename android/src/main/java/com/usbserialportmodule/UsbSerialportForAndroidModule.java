package com.usbserialportmodule;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbEndpoint;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver;
import com.hoho.android.usbserial.driver.FtdiSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;

import android.util.Base64;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@ReactModule(name = UsbSerialportForAndroidModule.NAME)
public class UsbSerialportForAndroidModule extends ReactContextBaseJavaModule implements EventSender {
  public static final String NAME = "UsbSerialportForAndroid";
  private static final String INTENT_ACTION_GRANT_USB = BuildConfig.LIBRARY_PACKAGE_NAME + ".GRANT_USB";

  public static final String CODE_DEVICE_NOT_FOND = "device_not_found";
  public static final String CODE_DRIVER_NOT_FOND = "driver_not_found";
  public static final String CODE_NOT_ENOUGH_PORTS = "not_enough_ports";
  public static final String CODE_PERMISSION_DENIED = "permission_denied";
  public static final String CODE_OPEN_FAILED = "open_failed";
  public static final String CODE_DEVICE_NOT_OPEN = "device_not_open";
  public static final String CODE_SEND_FAILED = "send_failed";
  public static final String CODE_DEVICE_NOT_OPEN_OR_CLOSED = "device_not_open_or_closed";

  private final ReactApplicationContext reactContext;
  private final Map<Integer, UsbSerialPortWrapper> usbSerialPorts = new HashMap<Integer, UsbSerialPortWrapper>();

  public UsbSerialportForAndroidModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("CODE_DEVICE_NOT_FOND", CODE_DEVICE_NOT_FOND);
    constants.put("CODE_DRIVER_NOT_FOND", CODE_DRIVER_NOT_FOND);
    constants.put("CODE_NOT_ENOUGH_PORTS", CODE_NOT_ENOUGH_PORTS);
    constants.put("CODE_PERMISSION_DENIED", CODE_PERMISSION_DENIED);
    constants.put("CODE_OPEN_FAILED", CODE_OPEN_FAILED);
    constants.put("CODE_DEVICE_NOT_OPEN", CODE_DEVICE_NOT_OPEN);
    constants.put("CODE_SEND_FAILED", CODE_SEND_FAILED);
    constants.put("CODE_DEVICE_NOT_OPEN_OR_CLOSED", CODE_DEVICE_NOT_OPEN_OR_CLOSED);
    return constants;
  }

  @ReactMethod
  public void list(Promise promise) {
    WritableArray devices = Arguments.createArray();
    UsbManager usbManager = (UsbManager) getCurrentActivity().getSystemService(Context.USB_SERVICE);
    for (UsbDevice device : usbManager.getDeviceList().values()) {
      WritableMap d = Arguments.createMap();
      d.putInt("deviceId", device.getDeviceId());
      d.putInt("vendorId", device.getVendorId());
      d.putInt("productId", device.getProductId());
      d.putString("name", device.getProductName());
      d.putString("deviceName", device.getDeviceName());
      d.putString("productName", device.getProductName());
      d.putString("version", device.getVersion());
      d.putString("manufacturerName", device.getManufacturerName());
      devices.pushMap(d);
    }
    promise.resolve(devices);
  }

  @ReactMethod
  public void tryRequestPermission(int deviceId, Promise promise) {
    UsbManager usbManager = (UsbManager) getCurrentActivity().getSystemService(Context.USB_SERVICE);
    UsbDevice device = findDevice(deviceId);
    if (device == null) {
      promise.reject(CODE_DEVICE_NOT_FOND, "device not found");
      return;
    }

    if (usbManager.hasPermission(device)) {
      promise.resolve(1);
      return;
    }

    PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getCurrentActivity(), 0,
        new Intent(INTENT_ACTION_GRANT_USB), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    usbManager.requestPermission(device, usbPermissionIntent);
    promise.resolve(0);
  }

  @ReactMethod
  public void hasPermission(int deviceId, Promise promise) {
    UsbManager usbManager = (UsbManager) getCurrentActivity().getSystemService(Context.USB_SERVICE);
    UsbDevice device = findDevice(deviceId);
    if (device == null) {
      promise.reject(CODE_DEVICE_NOT_FOND, "device not found");
      return;
    }

    promise.resolve(usbManager.hasPermission(device));
    return;
  }

  @ReactMethod
  public void open(int deviceId, int baudRate, int dataBits, int stopBits, int parity, Promise promise) {
    UsbSerialPortWrapper wrapper = usbSerialPorts.get(deviceId);
    if (wrapper != null) {
      WritableMap result = Arguments.createMap();
      result.putInt("deviceId", deviceId);
      result.putString("productName", wrapper.getPort().getDriver().getDevice().getProductName());
      promise.resolve(result);
      return;
    }

    UsbManager usbManager = (UsbManager) getCurrentActivity().getSystemService(Context.USB_SERVICE);
    UsbDevice device = findDevice(deviceId);
    if (device == null) {
      promise.reject("DEVICE_NOT_FOUND", "Device not found");
      return;
    }

    ProbeTable customTable = new ProbeTable();
    customTable.addProduct(0x2341, 0x0043, CdcAcmSerialDriver.class); // Arduino Uno
    customTable.addProduct(0x1a86, 0x7523, Ch34xSerialDriver.class); // CH340
    customTable.addProduct(0x10c4, 0xea60, Cp21xxSerialDriver.class); // CP210x
    customTable.addProduct(0x0403, 0x6001, FtdiSerialDriver.class); // FTDI

    UsbSerialProber prober = new UsbSerialProber(customTable);
    UsbSerialDriver driver = prober.probeDevice(device);

    if (driver == null) {
      driver = UsbSerialProber.getDefaultProber().probeDevice(device);
      if (driver == null) {
        promise.reject("DRIVER_NOT_FOUND", "No driver for device");
        return;
      }
    }

    if (driver.getPorts().size() < 1) {
      promise.reject("NOT_ENOUGH_PORTS", "Not enough ports at device");
      return;
    }

    UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
    if (connection == null) {
      if (!usbManager.hasPermission(driver.getDevice())) {
        promise.reject("PERMISSION_DENIED", "Connection failed: permission denied");
      } else {
        promise.reject("OPEN_FAILED", "Connection failed: open failed");
      }
      return;
    }

    UsbSerialPort port = driver.getPorts().get(0);
    try {
      port.open(connection);
      port.setParameters(baudRate, dataBits, stopBits, parity);
    } catch (IOException e) {
      try {
        port.close();
      } catch (IOException ignored) {
      }
      promise.reject("OPEN_FAILED", "Connection failed", e);
      return;
    }

    wrapper = new UsbSerialPortWrapper(deviceId, port, this);
    usbSerialPorts.put(deviceId, wrapper);
    startReading(wrapper); // Start reading data after opening the port

    WritableMap result = Arguments.createMap();
    result.putInt("deviceId", deviceId);
    result.putString("productName", driver.getDevice().getProductName());
    promise.resolve(result);
  }

  private void startReading(UsbSerialPortWrapper wrapper) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        byte[] buffer = new byte[1024];
        while (true) {
          try {
            int len = wrapper.getPort().read(buffer, 1000);
            if (len > 0) {
              String data = new String(buffer, 0, len, StandardCharsets.UTF_8);
              WritableMap event = Arguments.createMap();
              event.putString("data", data);
              sendEvent("usbSerialPortDataReceived", event);
            }
          } catch (IOException e) {
            e.printStackTrace();
            break;
          }
        }
      }
    }).start();
  }

  @ReactMethod
  public void send(int deviceId, String hexStr, Promise promise) {
    UsbSerialPortWrapper wrapper = usbSerialPorts.get(deviceId);
    if (wrapper == null) {
      promise.reject(CODE_DEVICE_NOT_OPEN, "device not open");
      return;
    }

    // Enviar o comando como string
    String command = hexStr + "\r\n"; // Adiciona uma nova linha ao comando
    byte[] data = command.getBytes();

    try {
      // Enviar o comando
      wrapper.send(data);

      // Esperar e ler a resposta do dispositivo
      byte[] buffer = new byte[1024]; // Tamanho do buffer de leitura
      int numBytesRead = wrapper.getPort().read(buffer, 2000); // Timeout de 2000ms para ler a resposta
      if (numBytesRead > 0) {
        String response = new String(buffer, 0, numBytesRead);
        promise.resolve(response); // Resolvendo a Promise com a resposta do dispositivo
      } else {
        promise.reject(CODE_SEND_FAILED, "no response from device");
      }
    } catch (IOException e) {
      promise.reject(CODE_SEND_FAILED, "send failed", e);
      return;
    }
  }

  @ReactMethod
  public void close(int deviceId, Promise promise) {
    UsbSerialPortWrapper wrapper = usbSerialPorts.get(deviceId);
    if (wrapper == null) {
      promise.reject(CODE_DEVICE_NOT_OPEN_OR_CLOSED, "serial port not open or closed");
      return;
    }

    wrapper.close();
    usbSerialPorts.remove(deviceId);
    promise.resolve(null);
  }

  public void sendEvent(final String eventName, final WritableMap event) {
    reactContext.runOnUiQueueThread(new Runnable() {
      @Override
      public void run() {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, event);
      }
    });
  }

  private UsbDevice findDevice(int deviceId) {
    UsbManager usbManager = (UsbManager) getCurrentActivity().getSystemService(Context.USB_SERVICE);
    for (UsbDevice device : usbManager.getDeviceList().values()) {
      if (device.getDeviceId() == deviceId) {
        return device;
      }
    }

    return null;
  }

  public static byte[] hexStringToByteArray(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
          + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }

  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }

  @ReactMethod
public void startListening(int vendorId, int productId) {
  // Usar a libusb para detectar o dispositivo com base no Vendor ID e Product ID
  UsbManager usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
  UsbDevice device = null;
  for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
    if (usbDevice.getVendorId() == vendorId && usbDevice.getProductId() == productId) {
      device = usbDevice;
      break;
    }
  }

  if (device == null) {
    // Dispositivo não encontrado
    return;
  }

  // Abrir a conexão com o dispositivo
  UsbDeviceConnection connection = usbManager.openDevice(device);
  if (connection == null) {
    // Não foi possível abrir a conexão com o dispositivo
    return;
  }

  // Reivindicar a interface do dispositivo
  UsbInterface usbInterface = device.getInterface(0); // A interface pode variar, dependendo do dispositivo
  if (!connection.claimInterface(usbInterface, true)) {
    // Não foi possível reivindicar a interface do dispositivo
    connection.close();
    return;
  }

  // Encontrar e abrir o endpoint de entrada
  UsbEndpoint endpointIn = null;
  for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
    UsbEndpoint endpoint = usbInterface.getEndpoint(i);
    if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
        && endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
      endpointIn = endpoint;
      break;
    }
  }

  if (endpointIn == null) {
    // Endpoint de entrada não encontrado
    connection.releaseInterface(usbInterface);
    connection.close();
    return;
  }

  // Iniciar uma thread para ler os dados do endpoint de entrada
  UsbEndpoint finalEndpointIn = endpointIn;
  new Thread(new Runnable() {
    @Override
    public void run() {
      byte[] buffer = new byte[1024]; // Tamanho do buffer de leitura
      while (true) { // Loop infinito para ouvir constantemente
        int numBytesRead = connection.bulkTransfer(finalEndpointIn, buffer, buffer.length, 1000); // Timeout de 1000ms
        if (numBytesRead > 0) {
          // Se dados foram lidos, notificar a camada JavaScript com os dados recebidos
          String data = new String(buffer, 0, numBytesRead);
          WritableMap event = Arguments.createMap();
          event.putString("data", data);
          sendEvent("usbDataReceived", event);
        }
      }
    }
  }).start();
}

  @ReactMethod
  public void writeToUsbPort(final int deviceId, final String command, final Promise promise) {
    UsbSerialPortWrapper wrapper = usbSerialPorts.get(deviceId);
    if (wrapper == null || wrapper.getPort() == null) {
      promise.reject("E_PORT_NOT_OPEN", "The USB port is not open or the device is not available");
      return;
    }

    try {
      byte[] data = command.getBytes(StandardCharsets.UTF_8);
      wrapper.getPort().write(data, 1000); // Timeout de 1000ms
      System.out.println("Sent command module: " + command);

      // Buffer para ler a resposta
      StringBuilder responseBuilder = new StringBuilder();
      byte[] buffer = new byte[2024];
      int len;

      // Continue reading until no data is received for 1 second
      long lastReadTime = System.currentTimeMillis();
      while (System.currentTimeMillis() - lastReadTime < 1000) {
        len = wrapper.getPort().read(buffer, 1000); // Timeout de 1000ms para cada leitura
        if (len > 0) {
          lastReadTime = System.currentTimeMillis(); // Reset the timer on successful read
          responseBuilder.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
        } else {
          // If no data is read, break the loop
          break;
        }
      }

      String response = responseBuilder.toString();
      System.out.println("Received response module: " + response);
      promise.resolve(response);
    } catch (IOException e) {
      promise.reject("E_WRITE_ERROR", "Failed to write command to USB port", e);
    }
  }
}
