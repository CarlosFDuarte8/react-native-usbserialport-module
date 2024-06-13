import type { EventEmitter, EventSubscription } from 'react-native';
import UsbSerialportForAndroid from './native_module';

const DataReceivedEvent = 'usbSerialPortDataReceived';
const ErrorEvent = 'Error';

export interface EventData {
  deviceId: number;
  /**
   * hex format
   */
  data: string;
}

export interface ErrorEventData {
  deviceId: number;
  error: string;
}

export type Listener = (data: EventData) => void;
export type ErrorListener = (error: ErrorEventData) => void;

export default class UsbSerial {
  deviceId: number;
  private eventEmitter: EventEmitter;
  private listeners: Listener[];
  private errorListeners: ErrorListener[];
  private subscriptions: EventSubscription[];
  private result: any;

  constructor(deviceId: number, eventEmitter: EventEmitter, result: any) {
    this.deviceId = deviceId;
    this.eventEmitter = eventEmitter;
    this.listeners = [];
    this.errorListeners = [];
    this.subscriptions = [];
    this.result = result;
  }

  /**
   * Send data with hex string.
   *
   * May return error with these codes:
   * * DEVICE_NOT_OPEN
   * * SEND_FAILED
   *
   * See {@link Codes}
   * @param hexStr
   * @returns
   */
  send(hexStr: string): Promise<null> {
    return UsbSerialportForAndroid.send(this.deviceId, hexStr);
  }

  async writeToUsbPort(base64: string): Promise<any> {
    try {
      await UsbSerialportForAndroid.writeToUsbPort(this.deviceId, base64);
      return null;
    } catch (error: any) {
      throw new Error(`Failed to send data: ${error.message}`);
    }
  }

  /**
   * Send data with a command string.
   *
   * May return error with these codes:
   * * DEVICE_NOT_OPEN
   * * SEND_FAILED
   *
   * See {@link Codes}
   * @param command
   * @returns
   */
  async sendCommand(command: string, addNewLine: boolean = true): Promise<any> {
    try {
      const formattedCommand = addNewLine ? `${command}\n` : command;
      const response = await UsbSerialportForAndroid.writeToUsbPort(
        this.deviceId,
        formattedCommand
      );
      console.log(`Sent command: ${formattedCommand}`);
      console.log(`Received response sendCommand: ${response}`);
      return response;
    } catch (error: any) {
      throw new Error(`Failed to send command: ${error.message}`);
    }
  }

  /**
   * Listen to data received event.
   *
   * @param listener
   * @returns EventSubscription
   */
  onReceived(listener: Listener): EventSubscription {
    const listenerProxy = (event: EventData) => {
      if (event.deviceId !== this.deviceId) {
        return;
      }
      if (!event.data) {
        return;
      }

      listener(event);
    };

    this.listeners.push(listenerProxy);
    const sub = this.eventEmitter.addListener(DataReceivedEvent, listenerProxy);
    this.subscriptions.push(sub);
    return sub;
  }

  /**
   * Listen to error events.
   *
   * @param listener
   * @returns EventSubscription
   */
  onError(listener: ErrorListener): EventSubscription {
    const listenerProxy = (event: ErrorEventData) => {
      if (event.deviceId !== this.deviceId) {
        return;
      }

      listener(event);
    };

    this.errorListeners.push(listenerProxy);
    const sub = this.eventEmitter.addListener(ErrorEvent, listenerProxy);
    this.subscriptions.push(sub);
    return sub;
  }

  /**
   * Close the connection and clean up resources.
   *
   * May return error with these codes:
   * * DEVICE_NOT_OPEN_OR_CLOSED
   *
   * See {@link Codes}
   * @returns Promise<null>
   */
  close(): Promise<any> {
    for (const sub of this.subscriptions) {
      sub.remove();
    }
    this.listeners = [];
    this.errorListeners = [];
    this.subscriptions = [];
    return UsbSerialportForAndroid.close(this.deviceId);
  }

  /**
   * Start listening for USB events.
   *
   * @param vendorId
   * @param productId
   * @returns Promise<any>
   */
  startListening(vendorId: number, productId: number): Promise<any> {
    const result = UsbSerialportForAndroid.startListening(vendorId, productId);
    console.log(result);
    return result;
  }

  /**
   * Monitor data received and error events.
   *
   * @param onDataReceived
   * @param onError
   * @returns { close: () => void }
   */
  monitor(
    onDataReceived: Listener,
    onError: ErrorListener
  ): { close: () => void } {
    const dataSub = this.onReceived(onDataReceived);
    const errorSub = this.onError(onError);

    return {
      close: () => {
        dataSub.remove();
        errorSub.remove();
      },
    };
  }
}
