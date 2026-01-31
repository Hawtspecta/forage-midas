package com.jpmc.midascore.service;

import com.jpmc.midascore.client.IncentiveClient;
import com.jpmc.midascore.entity.TransactionRecord;
import com.jpmc.midascore.entity.UserRecord;
import com.jpmc.midascore.foundation.Incentive;
import com.jpmc.midascore.foundation.Transaction;
import com.jpmc.midascore.repository.TransactionRepository;
import com.jpmc.midascore.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final IncentiveClient incentiveClient;

    public TransactionService(UserRepository userRepository, 
                             TransactionRepository transactionRepository,
                             IncentiveClient incentiveClient) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.incentiveClient = incentiveClient;
    }

    @Transactional
    public void processTransaction(Transaction transaction) {
        logger.info("Processing transaction: {}", transaction);

        // Validate sender exists
        UserRecord sender = userRepository.findById(transaction.getSenderId());
        if (sender == null) {
            logger.warn("Invalid transaction: Sender ID {} not found", transaction.getSenderId());
            return;
        }

        // Validate recipient exists
        UserRecord recipient = userRepository.findById(transaction.getRecipientId());
        if (recipient == null) {
            logger.warn("Invalid transaction: Recipient ID {} not found", transaction.getRecipientId());
            return;
        }

        // Validate sender has sufficient balance
        if (sender.getBalance() < transaction.getAmount()) {
            logger.warn("Invalid transaction: Sender {} has insufficient balance. Required: {}, Available: {}",
                    sender.getName(), transaction.getAmount(), sender.getBalance());
            return;
        }

        // Get incentive from API
        Incentive incentive = incentiveClient.getIncentive(transaction);
        float incentiveAmount = (incentive != null) ? incentive.getAmount() : 0;

        // All validations passed - process the transaction
        // Deduct amount from sender (incentive is NOT deducted from sender)
        sender.setBalance(sender.getBalance() - transaction.getAmount());
        
        // Add amount + incentive to recipient
        recipient.setBalance(recipient.getBalance() + transaction.getAmount() + incentiveAmount);

        // Save updated balances
        userRepository.save(sender);
        userRepository.save(recipient);

        // Record the transaction with incentive
        TransactionRecord transactionRecord = new TransactionRecord(sender, recipient, transaction.getAmount(), incentiveAmount);
        transactionRepository.save(transactionRecord);

        logger.info("Transaction processed successfully: {} -> {}, amount: {}, incentive: {}",
                sender.getName(), recipient.getName(), transaction.getAmount(), incentiveAmount);
    }
}