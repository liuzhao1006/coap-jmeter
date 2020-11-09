package net.xmeter.test;

import net.xmeter.samplers.CoAPPubSampler;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.engine.DefaultRegistrationEngineFactory;
import org.eclipse.leshan.client.object.Device;
import org.eclipse.leshan.client.object.Security;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.observer.LwM2mClientObserverAdapter;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.client.servers.ServerIdentity;
import org.eclipse.leshan.core.LwM2mId;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.StaticModel;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.core.request.RegisterRequest;
import org.eclipse.leshan.core.request.UpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class Client {
    private transient static Logger logger = LoggerFactory.getLogger(Client.class.getName());

    private LeshanClientBuilder builder;

    private final Object object = new Object();


    public void start() {
        synchronized (object) {
            String endpoint = UUID.randomUUID().toString().replaceAll("-", "");
            builder = new LeshanClientBuilder(endpoint);
            List<ObjectModel> models = ObjectLoader.loadDefault();
            final LwM2mModel model = new StaticModel(models);
            final ObjectsInitializer initializer = new ObjectsInitializer(model);
            initializer.setInstancesForObject(LwM2mId.SECURITY, Security.noSec("coap://127.0.0.1:5683", 12345));
            initializer.setInstancesForObject(LwM2mId.SERVER, new Server(12345, 30, BindingMode.U, false));
            initializer.setInstancesForObject(LwM2mId.DEVICE, new Device("Eclipse Leshan", "model12345", "12345", "U"));
//            initializer.setInstancesForObject(LwM2mId.CONNECTIVITY_STATISTICS, new ConnectivityStatistics());
            builder.setObjects(initializer.createAll());

            DefaultRegistrationEngineFactory engineFactory = new DefaultRegistrationEngineFactory();
            engineFactory.setCommunicationPeriod(30000);
            engineFactory.setReconnectOnUpdate(true);
            engineFactory.setResumeOnConnect(false);

            final LeshanClient client = builder.build();

            client.addObserver(new LwM2mClientObserverAdapter() {

                @Override
                public void onRegistrationSuccess(ServerIdentity server, RegisterRequest request, String registrationID) {
                    super.onRegistrationSuccess(server, request, registrationID);
                    logger.info("onRegistrationSuccess " + Thread.currentThread().getName());

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    synchronized (object) {
                        logger.info("onRegistrationSuccess sleep " + Thread.currentThread().getName());
                        object.notifyAll();
                    }
                }

                @Override
                public void onUpdateSuccess(ServerIdentity server, UpdateRequest request) {
                    super.onUpdateSuccess(server, request);
                    logger.info("onRegistrationSuccess " + Thread.currentThread().getName());
                }
            });
            client.start();

            logger.info("start end " + Thread.currentThread().getName());
            synchronized (object) {
                logger.info("end " + Thread.currentThread().getName());
                try {
                    object.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            synchronized (object) {
                logger.info("stop " + Thread.currentThread().getName());
                client.stop(true);
            }

        }
    }

    public static void main(String[] args) {
        logger.info("main " + Thread.currentThread().getName());
        Client client = new Client();
        client.start();
    }

}
