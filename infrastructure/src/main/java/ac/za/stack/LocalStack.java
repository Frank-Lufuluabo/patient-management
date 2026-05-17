package ac.za.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.msk.CfnCluster;

import java.util.List;
import java.util.stream.Collectors;

public class LocalStack extends Stack {
    private final Vpc vpc;
    private final Cluster ecsCluster;
    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);
        this.vpc = createVpc();
    }

    private Vpc createVpc() {
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2)
                .build();
    }

    private CfnCluster createMskCluster(){
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafa-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList()))
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    private Cluster createEcsCluster(){
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    private ApplicationLoadBalancedFargateService createGrafanaService(){
        FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
                .create(this, "GrafanaService")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        taskDefinition.addContainer("GrafanaContainer", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("grafana/grafana"))
                .portMappings(List.of(PortMapping.builder()
                        .containerPort(3000)
                        .build()))
                .build());

        ApplicationLoadBalancedFargateService service = ApplicationLoadBalancedFargateService.Builder
                .create(this, "GrafanaUIService")
                .taskDefinition(taskDefinition)
                .publicLoadBalancer(true)
                .listenerPort(3000)
                .desiredCount(1)
                .build();

        return service;
    }

    public static void main(String[] args) {
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
