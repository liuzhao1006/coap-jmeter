package net.xmeter.samplers;

import net.xmeter.Util;
import org.apache.jmeter.samplers.Entry;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.ThreadListener;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.engine.DefaultRegistrationEngineFactory;
import org.eclipse.leshan.client.object.Device;
import org.eclipse.leshan.client.object.Security;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.observer.LwM2mClientObserverAdapter;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.LwM2mId;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.StaticModel;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.text.MessageFormat;
import java.util.List;

public class CoAPPubSampler extends AbstractCoAPSampler implements ThreadListener {
    private static final long serialVersionUID = -4312341622759500786L;
    private transient static Logger logger = LoggerFactory.getLogger(CoAPPubSampler.class.getName());
    private String payload = null;
    private String clientId = "";
    private String uri;
    private String resourcePath;
    private String encodedResPath;
    private Request request;
    private String query;
    private LeshanClientBuilder builder;
    private SampleResult result;

    public String getMethodType() {
        return getPropertyAsString(METHOD_TYPE, DEFAULT_PUB_METHOD_TYPE);
    }

    public String getPayloadType() {
        return getPropertyAsString(PAYLOAD_TYPE, PAYLOAD_TYPE_RANDOM_STR_WITH_FIX_LEN);
    }

    public void setPayloadType(String payloadType) {
        setProperty(PAYLOAD_TYPE, payloadType);
    }

    public String getPayloadLength() {
        return getPropertyAsString(PAYLOAD_FIX_LENGTH, DEFAULT_PAYLOAD_FIX_LENGTH);
    }

    public void setPayloadLength(String length) {
        setProperty(PAYLOAD_FIX_LENGTH, length);
    }

    public String getPayload() {
        return getPropertyAsString(PAYLOAD_TO_BE_SENT, "");
    }

    public void setPayload(String message) {
        setProperty(PAYLOAD_TO_BE_SENT, message);
    }

    public boolean isAddTimestamp() {
        return getPropertyAsBoolean(ADD_TIMESTAMP);
    }

    public void setAddTimestamp(boolean addTimestamp) {
        setProperty(ADD_TIMESTAMP, addTimestamp);
    }

    public String getClienIdPrefix() {
        return getPropertyAsString(CONN_CLIENT_ID_PREFIX, DEFAULT_CONN_PREFIX_FOR_PUB);
    }

    @Override
    public SampleResult sample(Entry arg0) {
        result = new SampleResult();
        try {
            uri = "coap://" + getServer() + ":" + getPort();
            logger.info(uri);
            if(DEFAULT_PUB_METHOD_TYPE.equals(getMethodType())) {
                request = Request.newPut();
            } else {
                request = Request.newPost();
            }
            request.getOptions().setContentFormat(MediaTypeRegistry.APPLICATION_OCTET_STREAM);
            request.setURI(uri);

            result.setSampleLabel(getName());
            
            result.sampleStart();

            result.setResponseData(uri, "UTF-8");

            String endpoint = getEndpoint();
            builder = new LeshanClientBuilder(endpoint);
            List<ObjectModel> models = ObjectLoader.loadDefault();

            final LwM2mModel model = new StaticModel(models);
            final ObjectsInitializer initializer = new ObjectsInitializer(model);
            initializer.setInstancesForObject(LwM2mId.SECURITY, Security.noSec(uri, 12345));
            initializer.setInstancesForObject(LwM2mId.SERVER, new Server(12345, Long.parseLong(getLifeTime()), BindingMode.U, false));
            initializer.setInstancesForObject(LwM2mId.DEVICE, new Device("Eclipse Leshan", "model12345", "12345", "U"));
//            initializer.setInstancesForObject(LwM2mId.CONNECTIVITY_STATISTICS, new ConnectivityStatistics());
            builder.setObjects(initializer.createAll());

            DefaultRegistrationEngineFactory engineFactory = new DefaultRegistrationEngineFactory();
            engineFactory.setCommunicationPeriod(30000);
            engineFactory.setReconnectOnUpdate(true);
            engineFactory.setResumeOnConnect(false);

            final LeshanClient client = builder.build();
            client.addObserver(new LwM2mClientObserverAdapter(){
                @Override
                public void onRegistrationStarted(ServerIdentity server, RegisterRequest request) {
                    super.onRegistrationStarted(server, request);
                    result.setResponseMessage(MessageFormat.format("Publish failed to topic {0}.", getResourcePath()));
                }

                @Override
                public void onRegistrationSuccess(ServerIdentity server, RegisterRequest request, String registrationID) {
                    super.onRegistrationSuccess(server, request, registrationID);
                    result.setResponseMessage("register success.");
                    client.stop(true);
                    result.setSuccessful(true);
                    result.setResponseMessage("onRegistrationSuccess");
                    result.setResponseCodeOK();
                    result.sampleEnd();
                }
            });
            client.start();
        }
        catch (Exception e) {
            //logger.log(Priority.ERROR, e.getMessage(), e);
            logger.error(e.getMessage(), e);
            result.sampleEnd();
            result.setSuccessful(false);
            result.setResponseMessage(MessageFormat.format("Publish failed to topic {0}.", getResourcePath()));
            result.setResponseData("Failed.".getBytes());
            result.setResponseCode("500");
        } finally {
            result.sampleEnd();
        }
        return result;
    }

    @Override
    public void threadStarted() {
        System.out.println("thread Started!!!");

    }

    @Override
    public void threadFinished() {
        System.out.println("Pub thread Finished!!!");
    }

    public class ConnectivityStatistics extends BaseInstanceEnabler {

        @Override
        public ReadResponse read(ServerIdentity identity, int resourceid) {
            switch (resourceid) {
                case 0:
                    result.setSuccessful(true);
                    result.setResponseCode("200");
                    return ReadResponse.success(resourceid, "OK");
            }
            return ReadResponse.notFound();
        }

        @Override
        public WriteResponse write(ServerIdentity identity, int resourceid, LwM2mResource value) {
            switch (resourceid) {
                case 15:
                    // setCollectionPeriod((Long) value.getValue());
                    return WriteResponse.success();
            }
            return WriteResponse.notFound();
        }

        @Override
        public ExecuteResponse execute(ServerIdentity identity, int resourceid, String params) {
            switch (resourceid) {
                case 12:
                    return ExecuteResponse.success();
            }
            return ExecuteResponse.notFound();
        }
    }
}
