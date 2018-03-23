package org.ethereum.util;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.config.TestSystemProperties;
import co.rsk.core.Coin;
import co.rsk.core.ReversibleTransactionExecutor;
import co.rsk.core.RskAddress;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.TransactionPoolImpl;
import co.rsk.db.RepositoryImpl;
import co.rsk.test.builders.AccountBuilder;
import co.rsk.test.builders.TransactionBuilder;
import co.rsk.trie.TrieStoreImpl;
import co.rsk.validators.DummyBlockValidator;
import org.ethereum.core.*;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.*;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.vm.program.ProgramResult;
import org.ethereum.vm.program.invoke.ProgramInvokeFactoryImpl;

import java.math.BigInteger;
import java.util.HashMap;

/**
 * This is the test version of {@link co.rsk.core.RskFactory}, but without Spring.
 *
 * We try to recreate the objects used in production as best as we can,
 * replacing persistent storage with in-memory storage.
 * There are many nulls in place of objects that aren't part of our
 * tests yet.
 */
public class RskTestFactory {
    private final TestSystemProperties config = new TestSystemProperties();
    private BlockChainImpl blockchain;
    private IndexedBlockStore blockStore;
    private TransactionPool transactionPool;
    private RepositoryImpl repository;
    private ProgramInvokeFactoryImpl programInvokeFactory;
    private ReversibleTransactionExecutor reversibleTransactionExecutor;

    public RskTestFactory() {
        Genesis genesis = new BlockGenerator().getGenesisBlock();
        genesis.setStateRoot(getRepository().getRoot());
        genesis.flushRLP();
        getBlockchain().setBestBlock(genesis);
        getBlockchain().setTotalDifficulty(genesis.getCumulativeDifficulty());
    }

    public ContractDetails addContract(String runtimeBytecode) {
        Account contractAccount = new AccountBuilder(getBlockchain())
                .name(runtimeBytecode)
                .balance(Coin.valueOf(10))
                .code(TypeConverter.stringHexToByteArray(runtimeBytecode))
                .build();

        return getRepository().getContractDetails(contractAccount.getAddress());
    }

    public ProgramResult executeRawContract(byte[] bytecode, byte[] encodedCall, BigInteger value) {
        Account sender = new AccountBuilder(getBlockchain())
                .name("sender")
                // a large balance will allow running any contract
                .balance(Coin.valueOf(10000000L))
                .build();
        BigInteger nonceCreate = getRepository().getNonce(sender.getAddress());
        Transaction creationTx = new TransactionBuilder()
                .gasLimit(BigInteger.valueOf(3000000))
                .sender(sender)
                .data(bytecode)
                .nonce(nonceCreate.longValue())
                .build();
        executeTransaction(creationTx);
        BigInteger nonceExecute = getRepository().getNonce(sender.getAddress());
        Transaction transaction = new TransactionBuilder()
                // a large gas limit will allow running any contract
                .gasLimit(BigInteger.valueOf(3000000))
                .sender(sender)
                .receiverAddress(creationTx.getContractAddress().getBytes())
                .data(encodedCall)
                .nonce(nonceExecute.longValue())
                .value(value)
                .build();
        return executeTransaction(transaction).getResult();
    }

    private TransactionExecutor executeTransaction(Transaction transaction) {
        Repository track = getRepository().startTracking();
        TransactionExecutor executor = new TransactionExecutor(config, transaction, 0, RskAddress.nullAddress(),
                getRepository(), getBlockStore(), getReceiptStore(),
                getProgramInvokeFactory(), getBlockchain().getBestBlock());
        executor.init();
        executor.execute();
        executor.go();
        executor.finalization();
        track.commit();
        return executor;
    }

    private ProgramInvokeFactoryImpl getProgramInvokeFactory() {
        if (programInvokeFactory == null) {
            programInvokeFactory = new ProgramInvokeFactoryImpl();
        }

        return programInvokeFactory;
    }

    public BlockChainImpl getBlockchain() {
        if (blockchain == null) {
            blockchain = new BlockChainImpl(
                    config, getRepository(),
                    getBlockStore(),
                    getReceiptStore(),
                    null, //circular dependency
                    null,
                    null,
                    new DummyBlockValidator()
            );
            TransactionPool transactionPool = getTransactionPool();
            blockchain.setTransactionPool(transactionPool);
        }

        return blockchain;
    }

    public ReceiptStore getReceiptStore() {
        HashMapDB receiptStore = new HashMapDB();
        return new ReceiptStoreImpl(receiptStore);
    }

    public BlockStore getBlockStore() {
        if (blockStore == null) {
            this.blockStore = new IndexedBlockStore(new HashMap<>(), new HashMapDB(), null);
        }

        return blockStore;
    }

    public TransactionPool getTransactionPool() {
        if (transactionPool == null) {
            transactionPool = new TransactionPoolImpl(
                    getBlockStore(),
                    getReceiptStore(),
                    null,
                    getProgramInvokeFactory(),
                    getRepository(),
                    config
            );
        }

        return transactionPool;
    }

    public Repository getRepository() {
        if (repository == null) {
            HashMapDB stateStore = new HashMapDB();
            repository = new RepositoryImpl(config, new TrieStoreImpl(stateStore));
        }

        return repository;
    }

    public ReversibleTransactionExecutor getReversibleTransactionExecutor() {
        if (reversibleTransactionExecutor == null) {
            reversibleTransactionExecutor = new ReversibleTransactionExecutor(
                    config,
                    getRepository(),
                    getBlockStore(),
                    getReceiptStore(),
                    getProgramInvokeFactory()
            );
        }

        return reversibleTransactionExecutor;
    }
}
