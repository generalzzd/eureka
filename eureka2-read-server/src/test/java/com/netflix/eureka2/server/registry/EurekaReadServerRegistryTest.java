package com.netflix.eureka2.server.registry;

import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class EurekaReadServerRegistryTest {

    private static final Interest<InstanceInfo> INTEREST = Interests.forVips("testVip");
    private static final ChangeNotification<InstanceInfo> BUFFER_START = StreamStateNotification.bufferStartNotification(INTEREST);
    private static final ChangeNotification<InstanceInfo> BUFFER_END = StreamStateNotification.bufferEndNotification(INTEREST);

    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_1 = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.EurekaWriteServer.build());
    private static final ChangeNotification<InstanceInfo> ADD_INSTANCE_2 = new ChangeNotification<>(Kind.Add, SampleInstanceInfo.EurekaWriteServer.build());

    private final EurekaInterestClient interestClient = mock(EurekaInterestClient.class);
    private final PublishSubject<ChangeNotification<InstanceInfo>> interestSubject = PublishSubject.create();

    private final EurekaReadServerRegistry registry = new EurekaReadServerRegistry(interestClient);

    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    @Before
    public void setUp() throws Exception {
        when(interestClient.forInterest(any(Interest.class))).thenReturn(interestSubject);
        registry.forInterest(INTEREST).subscribe(testSubscriber);
    }

    @Test
    public void testVoidBufferSentinelsAreIgnored() throws Exception {
        // Buffer sentinel with no data ahead of it shall be swallowed
        interestSubject.onNext(ChangeNotification.<InstanceInfo>bufferSentinel());
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testBufferSentinelsAfterSingleItemDoNotGenerateBufferStartEndMarkers() throws Exception {
        // Buffer sentinel after single item doesn't generate buffer delineation
        interestSubject.onNext(ADD_INSTANCE_1);
        interestSubject.onNext(ChangeNotification.<InstanceInfo>bufferSentinel());
        assertThat(testSubscriber.takeNext(), is(ADD_INSTANCE_1));
        assertThat(testSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testBufferSentinelsAreTransformedToBufferStartEndMarkers() throws Exception {
        // Buffer sentinel after two or more items creates delineation markers
        interestSubject.onNext(ADD_INSTANCE_1);
        interestSubject.onNext(ADD_INSTANCE_2);
        interestSubject.onNext(ChangeNotification.<InstanceInfo>bufferSentinel());

        assertThat(testSubscriber.takeNext(), is(equalTo(BUFFER_START)));
        assertThat(testSubscriber.takeNext(), is(equalTo(ADD_INSTANCE_1)));
        assertThat(testSubscriber.takeNext(), is(equalTo(ADD_INSTANCE_2)));
        assertThat(testSubscriber.takeNext(), is(equalTo(BUFFER_END)));
    }
}