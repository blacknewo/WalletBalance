package jp.maki.wallettracker

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TransactionDao_Impl(
  __db: RoomDatabase,
) : TransactionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTransaction: EntityInsertAdapter<Transaction>

  private val __deleteAdapterOfTransaction: EntityDeleteOrUpdateAdapter<Transaction>
  init {
    this.__db = __db
    this.__insertAdapterOfTransaction = object : EntityInsertAdapter<Transaction>() {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `transactions` (`id`,`type`,`amount`,`memo`,`createdAt`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: Transaction) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.type)
        statement.bindLong(3, entity.amount.toLong())
        statement.bindText(4, entity.memo)
        statement.bindLong(5, entity.createdAt)
      }
    }
    this.__deleteAdapterOfTransaction = object : EntityDeleteOrUpdateAdapter<Transaction>() {
      protected override fun createQuery(): String = "DELETE FROM `transactions` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: Transaction) {
        statement.bindLong(1, entity.id)
      }
    }
  }

  public override suspend fun insert(transaction: Transaction): Long = performSuspending(__db,
      false, true) { _connection ->
    val _result: Long = __insertAdapterOfTransaction.insertAndReturnId(_connection, transaction)
    _result
  }

  public override suspend fun delete(transaction: Transaction): Int = performSuspending(__db, false,
      true) { _connection ->
    var _result: Int = 0
    _result += __deleteAdapterOfTransaction.handle(_connection, transaction)
    _result
  }

  public override fun getAllFlow(): Flow<List<Transaction>> {
    val _sql: String = "SELECT * FROM transactions ORDER BY createdAt DESC"
    return createFlow(__db, false, arrayOf("transactions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _cursorIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _cursorIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _cursorIndexOfAmount: Int = getColumnIndexOrThrow(_stmt, "amount")
        val _cursorIndexOfMemo: Int = getColumnIndexOrThrow(_stmt, "memo")
        val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Transaction> = mutableListOf()
        while (_stmt.step()) {
          val _item: Transaction
          val _tmpId: Long
          _tmpId = _stmt.getLong(_cursorIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_cursorIndexOfType)
          val _tmpAmount: Int
          _tmpAmount = _stmt.getLong(_cursorIndexOfAmount).toInt()
          val _tmpMemo: String
          _tmpMemo = _stmt.getText(_cursorIndexOfMemo)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_cursorIndexOfCreatedAt)
          _item = Transaction(_tmpId,_tmpType,_tmpAmount,_tmpMemo,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getRecentFlow(): Flow<List<Transaction>> {
    val _sql: String = "SELECT * FROM transactions ORDER BY createdAt DESC LIMIT 10"
    return createFlow(__db, false, arrayOf("transactions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _cursorIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _cursorIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _cursorIndexOfAmount: Int = getColumnIndexOrThrow(_stmt, "amount")
        val _cursorIndexOfMemo: Int = getColumnIndexOrThrow(_stmt, "memo")
        val _cursorIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "createdAt")
        val _result: MutableList<Transaction> = mutableListOf()
        while (_stmt.step()) {
          val _item: Transaction
          val _tmpId: Long
          _tmpId = _stmt.getLong(_cursorIndexOfId)
          val _tmpType: String
          _tmpType = _stmt.getText(_cursorIndexOfType)
          val _tmpAmount: Int
          _tmpAmount = _stmt.getLong(_cursorIndexOfAmount).toInt()
          val _tmpMemo: String
          _tmpMemo = _stmt.getText(_cursorIndexOfMemo)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_cursorIndexOfCreatedAt)
          _item = Transaction(_tmpId,_tmpType,_tmpAmount,_tmpMemo,_tmpCreatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getBalanceFlow(): Flow<Int?> {
    val _sql: String =
        "SELECT SUM(CASE WHEN type = 'income' THEN amount ELSE -amount END) FROM transactions"
    return createFlow(__db, false, arrayOf("transactions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int?
        if (_stmt.step()) {
          val _tmp: Int?
          if (_stmt.isNull(0)) {
            _tmp = null
          } else {
            _tmp = _stmt.getLong(0).toInt()
          }
          _result = _tmp
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM transactions"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
