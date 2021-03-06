/*
 * Copyright (C) 2017-present, Chenai Nakam(chenai.nakam@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hobby.chenai.nakam.autotx.core.coin

import hobby.chenai.nakam.autotx.core.exch.AbsExchange
import hobby.chenai.nakam.lang.TypeBring
import hobby.chenai.nakam.util.NumFmt

/**
  * @author Chenai Nakam(chenai.nakam@gmail.com)
  * @version 1.0, 25/05/2017
  */
abstract class AbsCoinGroup {
  groupSelf =>

  // COIN表示的是同一个对象（如BtcZone）下的路径依赖类型，BTC, CONG等属于BtcZone.COIN（或BtcZone.Token）类的实例，
  // 可以new BtcZone.COIN()创建新实例，但不是AbsCoinZone#AbsCoin的实例，不过后者可以用于模式匹配，从属范围更广。
  type COIN <: AbsCoin
  type UNIT <: COIN with Unt
  protected[coin] type GROUP <: AbsCoinGroup

  def unitStd: UNIT

  def make(count: Long, unit: UNIT): COIN

  override def toString = s"GROUP[${unitStd.name}]"

  abstract class AbsCoin(private[core] val count: Long) extends NumFmt
    with Equals with Ordered[COIN] with TypeBring[UNIT, COIN, AbsCoinGroup#AbsCoin] {
    require(count >= 0, s"[`Coin.count`溢出: $count].")

    val isCash: Boolean

    final val group: GROUP = groupSelf.asInstanceOf[GROUP]

    def unit: UNIT

    def value: Double = this / unit

    def +(that: COIN): COIN = make(this.count + that.count, unit)

    def -(that: COIN): COIN = make(this.count - that.count, unit)

    // 由于toString.formatted也会进行round操作，如果这里再进行round会越算越多：
    // 例如6.45 FEN, round后的count = 65(给最低单位多保留了1位，即64.5, round(64.5) = 65),
    // 最终toString的时候round(6.5) = 7. 因此这里直接进行toLong舍弃小数。
    def *(x: Double): COIN = make((this.count * x).toLong /*round*/ , unit)

    def /(x: Double): COIN = make((this.count / x).toLong /*round*/ , unit)

    def /(that: COIN): Double = this.count.toDouble / that.count

    /**
      * 将单位标准化。由于某些预定义的[作为枚举的]常量将自己本身作为了标准单位。
      */
    def std: COIN = if (unit eq unitStd) this else mod(unitStd)

    /**
      * 转换到参数指定单位。
      */
    // 注意unit的参数类型，在某些情况下，即使我们知道是同一个对象（如CnyZone单例对象）下的路径依赖类型，
    // 但无法用类型参数进行规约，导致编译器无法认为是同一个路径依赖类型。
    // 因此这里使用了更宽泛的类型并进行了类型转换，这意味着，如果在运行时类型确实不是同一个对象路径下的，那么会抛异常。
    def mod(unit: UNIT): COIN = if (unit eq this.unit) this else make(count, unit)

    //    protected def requireGroupSame(unit: AbsCoinGroup#Unt): Unit = {
    //      require(unit.group == group, s"unit group mismatch. require: $group, found: ${unit.group}")
    //    }

    override def compare(that: COIN) = this.count compare that.count

    def min(that: COIN): COIN = if (this < that) this else that.mod(unit)

    def max(that: COIN): COIN = if (this > that) this else that.mod(unit)

    // COIN 会被擦除，子类实现。
    // override def equals(any: scala.Any)
    // override def canEqual(that: Any)

    override def hashCode() = 41 * (41 + count.hashCode()) + group.hashCode

    /**
      * 在不同货币之间换算。
      *
      * @param that     目标单位。注意这个参数的类型与其它不同，一般参数类型COIN用于
      *                 同一个路径依赖类型，而本参数可以接受多个不同的路径依赖类型。
      * @param exchange 交易平台。
      * @return 若兑换成功，则返回值与 `that` 同类型；若不成功，则直接返回本对象。因此返回值的类型不确定。
      */
    def to(that: AbsCoinGroup#Unt)(implicit exchange: AbsExchange)
    : AbsCoinGroup#AbsCoin = if (that.group eq this.group) mod(that) else exchange.applyExch(this, that)

    //    protected def format: String = value formatted s"%.${unit.decmlFmt}f"

    override final def toString = if (unit eq this) unit.name else formatted(-1, unit.decmlFmt)(null)

    override protected def unitNameFmt = unit.nameFmt
  }

  // 不能用Unit, 会与系统类型冲突。
  trait Unt extends AbsCoin {
    override final def unit = this

    def name: String

    def nameFmt: String = name

    def decmlFmt: Int = decimals(count)

    def decimals(n: Double): Int = if (n == 1) 0 else 1 + decimals(n / 10)

    def <<(coin: AbsCoinGroup#AbsCoin): COIN = coin mod unit
  }
}
