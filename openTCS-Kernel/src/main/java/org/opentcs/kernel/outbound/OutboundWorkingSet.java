/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opentcs.kernel.outbound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.order.OutboundOrder;
import org.opentcs.data.order.OutboundOrder.State;
import org.opentcs.components.kernel.services.OrderEnableService;

/**
 * �������⴫�ʹ������䵽��ķּ���̨.
 * @author Henry
 */
public class OutboundWorkingSet {
  /**
   * ����̨��������ÿ������̨�����ڷּ�һ�����ⶩ��.
   */
  public static final int WORKING_ORDER_NUM = 4;
  /**
   * ����̨�б����ڱ�ʾ��ǰ������̨�Ϸּ�ĳ��ⶩ����.
   */
  private final List<TCSObjectReference<OutboundOrder>> workingSets = new ArrayList<>();
  /**
   * ��������������ڷֽ���ⶩ����������빤��̨�б�.
   */
  private final OrderEnableService orderEnableService;
  /**
   * ���㳤�ȵ�byte������Ϊͬ����.
   */
  private final byte[] lock = new byte[0];
  
  @Inject
  public OutboundWorkingSet(OrderEnableService orderEnableService) {
    this.orderEnableService = orderEnableService;
  }
  
  public List<TCSObjectReference<OutboundOrder>> getWorkingSets(){
    synchronized(lock){
      return workingSets;
    }
  }
  
  public void removePickingOrder(TCSObjectReference<OutboundOrder> pickingOrder){
    synchronized(lock){
      workingSets.remove(pickingOrder);
    }
  }
  
  public void enableOutboundOrder(){
    List<OutboundOrder> sortedOrders = 
        orderEnableService.fetchObjects(OutboundOrder.class,order -> order.hasState(State.WAITING))
            .stream()
            .sorted(Comparator.comparing(OutboundOrder::getDeadline))
            .collect(Collectors.toList());
    for(int i=0;i < WORKING_ORDER_NUM - workingSets.size();i++){
      synchronized(lock){
        workingSets.add(sortedOrders.get(i).getReference());
      }
    }
  }
}
