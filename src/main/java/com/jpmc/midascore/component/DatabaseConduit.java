package com.jpmc.midascore.component;

import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.jpmc.midascore.foundation.Incentive;

@Component
public class DatabaseConduit {
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    // Inside the DatabaseConduit class
    private final RestTemplate restTemplate = new RestTemplate();
    private final String incentiveApiUrl = "http://localhost:8080/incentive";

    public DatabaseConduit(UserRepository userRepository, TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    // METHOD 1: For the Test (UserPopulator) to save initial users
    public void save(UserRecord userRecord) {
        userRepository.save(userRecord);
    }

    // METHOD 2: For the KafkaConsumer to process transactions
    @Transactional
    public void save(Transaction transaction) {
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());

        if (sender != null && recipient != null && sender.getBalance() >= transaction.getAmount()) {

            // 1. Call the Incentive API
            Incentive incentiveResponse = restTemplate.postForObject(incentiveApiUrl, transaction, Incentive.class);
            float incentiveAmount = (incentiveResponse != null) ? incentiveResponse.getAmount() : 0f;

            // 2. Adjust Balances
            sender.setBalance(sender.getBalance() - transaction.getAmount());
            // Note: Recipient gets the amount PLUS the system bonus
            recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

            // 3. Save everything
            userRepository.save(sender);
            userRepository.save(recipient);

            TransactionRecord record = new TransactionRecord(sender, recipient, transaction.getAmount(),
                    incentiveAmount);
            record.setIncentive(incentiveAmount); // Store the bonus in the history
            transactionRepository.save(record);

            userRepository.findAll().forEach(user -> {
                if ("wilbur".equalsIgnoreCase(user.getName())) {
                    System.out.println(">>> TARGET USER FOUND: " + user.getName());
                    System.out.println(">>> WILBUR'S CURRENT BALANCE: " + user.getBalance());
                }
            });
        }
    }
}
