/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opentcs.data.order;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import static java.util.Objects.requireNonNull;
import java.util.Set;
import java.util.stream.Collectors;
import org.opentcs.data.ObjectHistory;
import org.opentcs.data.TCSObject;
import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.model.Bin;
import org.opentcs.data.model.Bin.SKU;
import static org.opentcs.data.order.TransportOrderHistoryCodes.ORDER_CREATED;

/**
 *
 * @author Henry
 */
public class OutboundOrder
    extends TCSObject<OutboundOrder>
    implements Serializable{
  
  /**
   * �ö�����SKU��������.
   */
  private Set<SKU> requiredSKUs = new HashSet<>();
  /**
   * �ö�����δԤ������SKU����.
   */
  private Set<SKU> leftSKUs = new HashSet<>();
  /**
   * �ö����Ļ�����������Щ����������.
   */
  private Set<TCSObjectReference<Bin>> assignedBins = new HashSet<>();
  /**
   * �ö�������Ԥ��������SKU��.
   */
  private double reservedAmount = 0;
  /**
   * �ö������ѷּ������SKU��.
   */
  private double pickedAmount = 0;
  /**
   * �ö���Ҫ�����SKU��.
   */
  private double totalAmount = 0;
  /**
   * �ó��ⶩ����״̬.
   */
  private State state = State.WAITING;
  /**
   * �ö����Ĵ���ʱ��.
   */
  private final Instant creationTime;
  /**
   * �ö�����DDL.
   */
  private Instant deadline = Instant.ofEpochMilli(Long.MAX_VALUE);
  /**
   * �ö������������ʱ��.
   */
  private Instant finishedTime = Instant.ofEpochMilli(Long.MAX_VALUE);

  public OutboundOrder(String name) {
    super(name,
          new HashMap<>(),
          new ObjectHistory().withEntryAppended(new ObjectHistory.Entry(ORDER_CREATED)));
    this.creationTime = Instant.now();
    this.deadline = Instant.ofEpochMilli(Long.MAX_VALUE);
  }

  public OutboundOrder(String name,
                        Map<String, String> properties, 
                        ObjectHistory history,
                        Set<SKU> requiredSKUs,
                        Set<SKU> leftSKUs,
                        Set<TCSObjectReference<Bin>> assignedBins,
                        double reservedAmount,
                        double pickedAmount,
                        double totalAmount,
                        State state,
                        Instant creationTime, 
                        Instant deadline,
                        Instant finishedTime){ 
    super(name,properties,history);
    this.requiredSKUs = requireNonNull(requiredSKUs,"requiredSKUs");
    this.leftSKUs = requireNonNull(leftSKUs,"notAssignedSKUs");
    this.assignedBins = requireNonNull(assignedBins,"assignedBins");
    this.reservedAmount = reservedAmount;
    this.pickedAmount = pickedAmount;
    this.totalAmount = totalAmount;
    this.state = requireNonNull(state,"state");
    this.creationTime = requireNonNull(creationTime, "creationTime");
    this.deadline = requireNonNull(deadline, "deadline");
    this.finishedTime = requireNonNull(finishedTime, "finishedTime");
  }
  
  @Override
  public OutboundOrder withProperty(String key, String value) {
    return new OutboundOrder(getName(),
                            propertiesWith(key, value), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  @Override
  public OutboundOrder withProperties(Map<String, String> properties) {
    return new OutboundOrder(getName(),
                            properties, 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  @Override
  public OutboundOrder withHistoryEntry(ObjectHistory.Entry entry) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory().withEntryAppended(entry),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  @Override
  public OutboundOrder withHistory(ObjectHistory history) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            history,
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  public Set<SKU> getRequiredSKUs() {
    return requiredSKUs;
  }

  public OutboundOrder withRequiredSKUs(Set<SKU> requiredSKUs) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  public Set<SKU> getLeftSKUs() {
    return leftSKUs;
  }
  
  public OutboundOrder afterReservation(Set<SKU> reservedSKUs){
    // δԤ������SKU�����
    Map<String, Double> leftSkuMap = leftSKUs.stream()
        .collect(Collectors.toMap(SKU::getSkuID,SKU::getQuantity));
    
    // ����Ԥ��������δԤ������SKU�����
    reservedSKUs.forEach(sku -> {
      Double leftQuantity = leftSkuMap.get(sku.getSkuID()) - sku.getQuantity();
      leftSkuMap.put(sku.getSkuID(), leftQuantity);
    });
    
    // Ԥ�����󣬽�ʣ�µ�SKU������װ��SKU���ϣ�ͬʱͳ��ʣ�µ�SKU��������
    Double newLeftAmount = 0.0;
    Set<SKU> newLeftSKUs = new HashSet<>();
    for(Map.Entry<String,Double> skuEntry : leftSkuMap.entrySet()){
      if(skuEntry.getValue() > 0){
        newLeftSKUs.add(new SKU(skuEntry.getKey(),skuEntry.getValue()));
        newLeftAmount += skuEntry.getValue();
      }
    }
    // ���¸ó��ⶩ��ʣ�µ�SKU���󼯺�
    // ͬʱ����ʣ�µ�SKU�����������������Ԥ����SKU��
    this.leftSKUs = newLeftSKUs;
    this.reservedAmount = this.totalAmount - newLeftAmount;
    return this;
  }
  
  public OutboundOrder afterPicking(Set<SKU> pickedSKUs){
    Double skuAmount = pickedSKUs.stream().map(SKU::getQuantity).reduce(Double::sum).orElse(0.0);
    this.pickedAmount += skuAmount;
    return this;
  }
  
  public Set<TCSObjectReference<Bin>> getAssignedBins() {
    return assignedBins;
  }

  public OutboundOrder withAssignedBins(Set<TCSObjectReference<Bin>> assignedBins) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  public double getReservedAmount() {
    return reservedAmount;
  }

  public OutboundOrder withreservedAmount(double reservedAmount) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  public double getPickedAmount() {
    return pickedAmount;
  }

  public OutboundOrder withPickedAmount(double pickedAmount) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  public OutboundOrder updateTotalAmount(){
    totalAmount = requiredSKUs.stream().map(SKU::getQuantity).reduce(Double::sum).orElse(0.0);
    return this;
  }
  
  public double getTotalAmount() {
    return totalAmount;
  }

  public OutboundOrder withTotalAmount(double totalAmount) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }
  
  public Double getReservedCompletion(){
    // ��ȡ�ó��ⶩ����Ԥ����ɽ���
    return reservedAmount / totalAmount;
  }
  
  public Double getPickedCompletion(){
    // ��ȡ�ó��ⶩ���ļ�ѡ��ɽ���
    return pickedAmount / totalAmount;
  }

  public State getState() {
    return state;
  }
  
  public boolean hasState(State state){
    return this.state == state;
  }

  public OutboundOrder withState(State state) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  public Instant getCreationTime() {
    return creationTime;
  }
  
  public Instant getDeadline() {
    return deadline;
  }

  public OutboundOrder withDeadline(Instant deadline) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }

  public Instant getFinishedTime() {
    return finishedTime;
  }

  public OutboundOrder withFinishedTime(Instant finishedTime) {
    return new OutboundOrder(getName(),
                            getProperties(), 
                            getHistory(),
                            requiredSKUs, leftSKUs,
                            assignedBins,
                            reservedAmount,
                            pickedAmount,
                            totalAmount,
                            state,
                            creationTime, 
                            deadline,
                            finishedTime);
  }
  
  public enum State {
    WAITING,
    WORKING,
    FINISHED,
    FAILED;
    
    public boolean isFinalState() {
      return this.equals(FINISHED)
          || this.equals(FAILED);
    }
  }
}
