package com.usbserialportmodule;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class UsbSerialPortWrapper implements SerialInputOutputManager.Listener {
  private static final int WRITE_WAIT_MILLIS = 2000;
  private static final int READ_WAIT_MILLIS = 2000;
  private static final String DataReceivedEvent = "usbSerialPortDataReceived";
  private static final String ErrorEvent = "Error";

  private int deviceId;
  private UsbSerialPort port;
  private EventSender sender;
  private boolean closed = false;
  private SerialInputOutputManager ioManager;
  private ByteArrayOutputStream accumulatedData = new ByteArrayOutputStream();

  UsbSerialPortWrapper(int deviceId, UsbSerialPort port, EventSender sender) {
    this.deviceId = deviceId;
    this.port = port;
    this.sender = sender;
    this.ioManager = new SerialInputOutputManager(port, this);
    ioManager.start();
  }

  public void send(byte[] data) throws IOException {
    this.port.write(data, WRITE_WAIT_MILLIS);
  }

  public void onNewData(byte[] data) {
    try {
      WritableMap event = Arguments.createMap();
      String hex = UsbSerialportForAndroidModule.bytesToHex(data);
      String value = new String(data, StandardCharsets.ISO_8859_1);
      WritableArray byteArrayWritable = Arguments.createArray();

      for (byte b : data) {
        byteArrayWritable.pushInt(b & 0xFF); // Converte byte para inteiro sem sinal e adiciona ao array
      }

      event.putInt("deviceId", this.deviceId);
      event.putString("data", hex);
      event.putString("value", value);
      event.putArray("rawData", byteArrayWritable);

      Log.d("usbserialport", "Data received (hex): " + hex);
      Log.d("usbserialport", "Data received (value): " + value);

      sender.sendEvent(DataReceivedEvent, event);
    } catch (Exception e) { // Trata todas as exceções inesperadas
      WritableMap errorEvent = Arguments.createMap();
      errorEvent.putInt("deviceId", this.deviceId);
      errorEvent.putString("error", "Unexpected error");
      Log.e("usbserialport", "Unexpected error", e);
      sender.sendEvent(ErrorEvent, errorEvent);
    }
  }

  public void onRunError(Exception e) {
    // Manipulação de erros de execução, incluindo desconexão do dispositivo
    WritableMap errorEvent = Arguments.createMap();
    errorEvent.putInt("deviceId", this.deviceId);
    errorEvent.putString("error", "Run error: " + e.getMessage());
    Log.e("usbserialport", "Run error", e);
    sender.sendEvent(ErrorEvent, errorEvent);

    // Interromper o ioManager e liberar recursos
    if (ioManager != null) {
        ioManager.stop();
    }
  }

  public void close() {
    if (closed) {
      return;
    }

    if (ioManager != null) {
      ioManager.setListener(null);
      ioManager.stop();
    }

    this.closed = true;
    try {
      port.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public UsbSerialPort getPort() {
    return port;
  }
}
