/*
 * Copyright (c) 2018, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.meshprovisioner.configuration;

import android.content.Context;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import no.nordicsemi.android.meshprovisioner.InternalTransportCallbacks;
import no.nordicsemi.android.meshprovisioner.MeshConfigurationStatusCallbacks;
import no.nordicsemi.android.meshprovisioner.R;
import no.nordicsemi.android.meshprovisioner.messages.AccessMessage;
import no.nordicsemi.android.meshprovisioner.messages.ControlMessage;
import no.nordicsemi.android.meshprovisioner.messages.Message;
import no.nordicsemi.android.meshprovisioner.models.SigModel;
import no.nordicsemi.android.meshprovisioner.opcodes.ConfigMessageOpCodes;
import no.nordicsemi.android.meshprovisioner.utils.Element;
import no.nordicsemi.android.meshprovisioner.utils.MeshParserUtils;

import static no.nordicsemi.android.meshprovisioner.configuration.ConfigModelSubscriptionStatus.SubscriptionStatus.fromStatusCode;


public class ConfigModelSubscriptionStatus extends ConfigMessage {

    private static final String TAG = ConfigModelAppStatus.class.getSimpleName();
    private static final int CONFIG_MODEL_PUBLICATION_STATUS_SIG_MODEL_PDU_LENGTH = 9;
    private static final int CONFIG_MODEL_APP_BIND_STATUS_VENDOR_MODEL_PDU_LENGTH = 11;
    private int status;
    private byte[] elementAddress;
    private byte[] mSubscriptionAddress;
    private byte[] modelIdentifier; //16-bit SIG Model or 32-bit Vendor Model identifier
    private boolean isSuccessful;
    private String statusMessage;
    private final int messageType;

    public ConfigModelSubscriptionStatus(Context context, final ProvisionedMeshNode meshNode, final int messageType, final InternalTransportCallbacks transportCallbacks, final MeshConfigurationStatusCallbacks mMeshConfigurationStatusCallbacks) {
        super(context, meshNode);
        this.messageType = messageType;
        this.mInternalTransportCallbacks = transportCallbacks;
        this.mConfigStatusCallbacks = mMeshConfigurationStatusCallbacks;
    }

    public static String parseStatusMessage(final Context context, final int status) {
        switch (fromStatusCode(status)) {
            case SUCCESS:
                return context.getString(R.string.success);
            case INVALID_ADDRESS:
                return context.getString(R.string.status_invalid_address);
            case INVALID_MODEL:
                return context.getString(R.string.status_invalid_model);
            case INVALID_APPKEY_INDEX:
                return context.getString(R.string.status_invalid_appkey_index);
            case INVALID_NETKEY_INDEX:
                return context.getString(R.string.status_invalid_netkey_index);
            case INSUFFICIENT_RESOURCES:
                return context.getString(R.string.status_insufficient_resources);
            case KEY_INDEX_ALREADY_STORED:
                return context.getString(R.string.status_key_index_already_stored);
            case INVALID_PUBLISH_PARAMETERS:
                return context.getString(R.string.status_invalid_publish_parameters);
            case NOT_A_SUBSCRIBE_MODEL:
                return context.getString(R.string.status_not_a_subscribe_model);
            case STORAGE_FAILURE:
                return context.getString(R.string.status_storage_failure);
            case FEATURE_NOT_SUPPORTED:
                return context.getString(R.string.status_feature_not_supported);
            case CANNOT_UPDATE:
                return context.getString(R.string.status_cannot_update);
            case CANNOT_REMOVE:
                return context.getString(R.string.status_cannot_remove);
            case CANNOT_BIND:
                return context.getString(R.string.status_cannot_bind);
            case TEMPORARILY_UNABLE_TO_CHANGE_STATE:
                return context.getString(R.string.status_temporarily_unable_to_change_state);
            case CANNOT_SET:
                return context.getString(R.string.status_cannot_set);
            case UNSPECIFIED_ERROR:
                return context.getString(R.string.status_unspecified_error);
            case INVALID_BINDING:
                return context.getString(R.string.status_success_invalid_binding);
            case RFU:
            default:
                return context.getString(R.string.app_key_status_rfu);
        }

    }

    @Override
    public MessageState getState() {
        return MessageState.CONFIG_MODEL_SUBSCRIPTION_STATUS;
    }

    public final void parseData(final byte[] pdu) {
        parseMessage(pdu);
    }

    private void parseMessage(final byte[] pdu) {
        final Message message = mMeshTransport.parsePdu(mSrc, pdu);
        if (message != null) {
            if (message instanceof AccessMessage) {
                final AccessMessage accessMessage = (AccessMessage) message;
                final byte[] accessPayload = accessMessage.getAccessPdu();

                //MSB of the first octet defines the length of opcodes.
                //if MSB = 0 length is 1 and so forth
                final int opCodeLength = ((accessPayload[0] >> 7) & 0x01) + 1;

                final int opcode = MeshParserUtils.getOpCode(accessPayload, opCodeLength);

                if (opcode == ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_STATUS) {
                    Log.v(TAG, "Received model subscription status");
                    final int offset = +2; //Ignoring the opcode and the parameter received
                    status = accessPayload[offset];
                    elementAddress = new byte[]{accessPayload[4], accessPayload[3]};
                    mSubscriptionAddress = new byte[]{accessPayload[6], accessPayload[5]};

                    if (accessPayload.length == CONFIG_MODEL_PUBLICATION_STATUS_SIG_MODEL_PDU_LENGTH) {
                        modelIdentifier = new byte[]{accessPayload[8], accessPayload[7]};
                    } else {
                        modelIdentifier = new byte[]{accessPayload[8], accessPayload[7], accessPayload[10], accessPayload[9]};
                    }

                    statusMessage = parseStatusMessage(mContext, status);
                    parseStatus(status);
                    Log.v(TAG, "Status: " + status);
                    Log.v(TAG, "Status message: " + statusMessage);
                    Log.v(TAG, "Element Address: " + MeshParserUtils.bytesToHex(elementAddress, false));
                    Log.v(TAG, "Subscription Address: " + MeshParserUtils.bytesToHex(mSubscriptionAddress, false));
                    Log.v(TAG, "Model Identifier: " + MeshParserUtils.bytesToHex(modelIdentifier, false));
                    if (isSuccessful) {
                        final Element element = mProvisionedMeshNode.getElements().get(getElementAddressInt());
                        final MeshModel model = element.getMeshModels().get(getModelIdentifierInt());
                        if (messageType == ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_ADD) {
                            model.setSubscriptionAddress(mSubscriptionAddress);
                        } else if (messageType == ConfigMessageOpCodes.CONFIG_MODEL_SUBSCRIPTION_DELETE) {
                            model.removeSubscriptionAddress(mSubscriptionAddress);
                        }
                    }
                    mInternalTransportCallbacks.updateMeshNode(mProvisionedMeshNode);
                    mConfigStatusCallbacks.onSubscriptionStatusReceived(mProvisionedMeshNode, isSuccessful, status, elementAddress, mSubscriptionAddress, getModelIdentifierInt());
                } else {
                    mConfigStatusCallbacks.onUnknownPduReceived(mProvisionedMeshNode);
                }
            } else {
                parseControlMessage((ControlMessage) message);
            }
        } else {
            Log.v(TAG, "Message reassembly may not be complete yet");
        }
    }

    @Override
    public void sendSegmentAcknowledgementMessage(final ControlMessage controlMessage) {
        final ControlMessage message = mMeshTransport.createSegmentBlockAcknowledgementMessage(controlMessage);
        Log.v(TAG, "Sending acknowledgement: " + MeshParserUtils.bytesToHex(message.getNetworkPdu().get(0), false));
        mInternalTransportCallbacks.sendPdu(mProvisionedMeshNode, message.getNetworkPdu().get(0));
        mConfigStatusCallbacks.onBlockAcknowledgementSent(mProvisionedMeshNode);
    }

    public int getStatus() {
        return status;
    }

    public byte[] getElementAddress() {
        return elementAddress;
    }

    /**
     * Returns the element address as int
     *
     * @return element address
     */
    private int getElementAddressInt() {
        return ByteBuffer.wrap(elementAddress).order(ByteOrder.BIG_ENDIAN).getShort();
    }

    /**
     * Returns the subscriped address indexes
     *
     * @return subscription address
     */
    public byte[] getSubscriptionAddress() {
        return mSubscriptionAddress;
    }


    /**
     * Returns the subscriped address indexes
     *
     * @return subscription address
     */
    public int getSubscriptionAddressInt() {
        return ByteBuffer.wrap(mSubscriptionAddress).order(ByteOrder.BIG_ENDIAN).getShort();
    }

    /**
     * Returns the model identifier which could be a 16-bit SIG Model ID or 32-bit Vendor Model ID
     *
     * @return modelIdentifier
     */
    public byte[] getModelIdentifier() {
        return modelIdentifier;
    }

    /**
     * Returns the model identifier as int which could be a 16-bit SIG Model ID or 32-bit Vendor Model ID
     *
     * @return appkeyindex int
     */
    private int getModelIdentifierInt() {
        if (modelIdentifier.length == SigModel.MODEL_ID_LENGTH) {
            return ByteBuffer.wrap(modelIdentifier).order(ByteOrder.BIG_ENDIAN).getShort();
        } else {
            return ByteBuffer.wrap(modelIdentifier).order(ByteOrder.BIG_ENDIAN).getInt();
        }
    }

    /**
     * Returns a boolean containing the success state
     *
     * @return true if successful and false otherwise
     */
    public boolean isSuccessful() {
        return isSuccessful;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    private void parseStatus(final int status) {
        switch (fromStatusCode(status)) {
            case SUCCESS:
                isSuccessful = true;
                break;
        }
    }

    public enum SubscriptionStatus {
        SUCCESS(0x00),
        INVALID_ADDRESS(0x01),
        INVALID_MODEL(0x02),
        INVALID_APPKEY_INDEX(0x03),
        INVALID_NETKEY_INDEX(0x04),
        INSUFFICIENT_RESOURCES(0x05),
        KEY_INDEX_ALREADY_STORED(0x06),
        INVALID_PUBLISH_PARAMETERS(0x07),
        NOT_A_SUBSCRIBE_MODEL(0x08),
        STORAGE_FAILURE(0x09),
        FEATURE_NOT_SUPPORTED(0x0A),
        CANNOT_UPDATE(0x0B),
        CANNOT_REMOVE(0x0C),
        CANNOT_BIND(0x0D),
        TEMPORARILY_UNABLE_TO_CHANGE_STATE(0x0E),
        CANNOT_SET(0x0F),
        UNSPECIFIED_ERROR(0x10),
        INVALID_BINDING(0x11),
        RFU(0x12);

        private final int statusCode;

        SubscriptionStatus(final int statusCode) {
            this.statusCode = statusCode;
        }

        public static SubscriptionStatus fromStatusCode(final int statusCode) {
            for (SubscriptionStatus failureCode : values()) {
                if (failureCode.getStatusCode() == statusCode) {
                    return failureCode;
                }
            }
            throw new IllegalArgumentException("Enum not found in AppKeyStatus");
        }

        public final int getStatusCode() {
            return statusCode;
        }
    }
}
