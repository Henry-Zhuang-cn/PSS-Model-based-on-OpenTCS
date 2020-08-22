/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opentcs.components.kernel.services;

import org.opentcs.data.TCSObject;
import org.opentcs.data.model.Bin;
import org.opentcs.data.model.Vehicle;

/**
 * �����ֽ����ӿ�
 * @author Henry
 */
public interface OrderDecompositionService 
    extends TCSObjectService{
  /**
   * ����ָ�������䣬���ɽ������������������䶩��.
   * @param bin ָ��������.
   * @param idleVehicle ָ���Ŀ��г���
   * @param order ������ⶩ��
   */
  void createTransportOrderForBin(Bin bin, Vehicle idleVehicle, TCSObject<?> order);
  /**
   * Ϊ���ⶩ��Ԥ�����ʵ����䣬�����������İ������񣬽�����������PSBȥ����.
   */
  void decomposeOutboundOrder();
  /**
   * ����ÿ����Ϳ�λվ�Ĺ����Ϣ�������ں˴���ģ��ʱ����һ��.
   * <p>�����Ϣ��Ҫ����������һ��PSB���(psbTrack)���ʹ�����һ��PST���(pstTrack).
   * </p>ע�⣺�˴���PST���ָ������PSB�����ֱ�ķ����ϵ�λ�ã��������Ϊ�����еĹ�ϵ.
   */
  void updateTrackInfo();
  
  String[][] getLocPosition();
}
