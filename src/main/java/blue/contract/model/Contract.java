package blue.contract.model;

import blue.contract.model.subscription.ContractSubscription;
import blue.language.model.TypeBlueId;

import java.util.List;

import static blue.contract.utils.Constants.BLUE_CONTRACTS_V04;

@TypeBlueId(defaultValueRepositoryDir = BLUE_CONTRACTS_V04)
public abstract class Contract {
    private String name;
    private String description;
    private Messaging messaging;
    private List<ContractSubscription> subscriptions;
    private List<Workflow> workflows;

    public String getName() {
        return name;
    }

    public Contract name(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Contract description(String description) {
        this.description = description;
        return this;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public Contract messaging(Messaging messaging) {
        this.messaging = messaging;
        return this;
    }

    public List<Workflow> getWorkflows() {
        return workflows;
    }

    public Contract workflows(List<Workflow> workflows) {
        this.workflows = workflows;
        return this;
    }

    public List<ContractSubscription> getSubscriptions() {
        return subscriptions;
    }

    public Contract subscriptions(List<ContractSubscription> subscriptions) {
        this.subscriptions = subscriptions;
        return this;
    }
}
