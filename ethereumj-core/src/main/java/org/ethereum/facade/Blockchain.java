package org.ethereum.facade;

import org.ethereum.core.Block;
import org.ethereum.core.Transaction;
import org.ethereum.db.BlockStore;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

public interface Blockchain {

    /**
     * Get block by number from the best chain
     * @param number - number of the block
     * @return block by that number
     */
    Block getBlockByNumber(long number);

    /**
     * Get block by hash
     * @param hash - hash of the block
     * @return - bloc by that hash
     */
    Block getBlockByHash(byte[] hash);

    /**
     * Get total difficulty from the start
     * and until the head of the chain
     *
     * @return - total difficulty
     */
    BigInteger getTotalDifficulty();

    /**
     * Get the underlying BlockStore
     * @return Blockstore
     */
    BlockStore getBlockStore();


    /**
     * @return - last added block from blockchain
     */
    Block getBestBlock();

    /**
     * Flush the content of local storage objects to disk
     */
    void flush();
}
