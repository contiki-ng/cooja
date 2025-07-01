/**
 *
 */
package se.sics.mspsim.core;

/**
 * @author joakim
 *
 */
interface InterruptHandler {
  // We should add "Interrupt serviced..." to indicate that its latest
  // Interrupt was serviced...
  void interruptServiced(int vector);
  String getName();
}
