/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opentcs.components.kernel.services;

import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.model.Bin;
import org.opentcs.data.model.Point;
import org.opentcs.data.model.Vehicle;
import org.opentcs.data.order.TransportOrder;

/**
 *
 * @author Henry
 */
public interface ChangeTrackService 
    extends TCSObjectService {
  /**
   * �����޳�����б�.
   * ���������Ҫά��һ���б��Լ�¼��Щ�����ǰû��PSB����û��PSB��Ҫǰ���ù��.
   */
  void updateTrackList();
  
  /**
   * ����޳�����б�
   */
  void clear();
  
  /**
   * ���ѻ�����񣬳���״̬�����仯�����������Ҫ�����޳�����б�.
   */
  void setVehicleStateChanged();
  
  /**
   * ��ָ��������PSB�����쵽ָ���������ڵĹ��.
   * @param bin ָ������
   * @param vehicleName ָ������ID
   * @return ���ɵ�ָ��������PSB���Ļ�������ID.
   */
  String createChangeTrackOrder(Bin bin, Vehicle vehicleName);
  
  /**
   * ����PSB��PST�Ѿ�λ.
   * @param orderName ��������ID
   */
  void notifyBinVehicle(String orderName);
  /**
   * ����PST��PSB�Ѿ�λ
   * @param orderName ��������ID
   */
  void notifyTrackVehicle(String orderName);
  
  /**
   * ���ݻ�����������״̬�����»�������غ��޳�����б�.
   * @param orderRef �������������
   * @param state ���������״̬
   */
  void updateTrackOrder(TCSObjectReference<TransportOrder> orderRef, TransportOrder.State state);
  
  /**
   * �жϼ��������Ŀ����Ƿ�Ϊָ���������񼯵ĵ�һ�����.
   * <p>PSB��Ҫ���������жϣ����ж�Ϊtrueʱ��PSB��Ҫ�ȴ�PST��λ.</p>
   * @param dstPoint ���������Ŀ���.
   * @param orderName ��������ID
   * @return ���ҽ������������Ŀ�����ָ���������񼯵ĵ�һ�����ʱ������{@code true}.
   */
  boolean isEnteringFirstTrackPoint(Point dstPoint, String orderName);
  
  /**
   * �жϼ����뿪����ʼ���Ƿ�Ϊָ���������񼯵ĵ�һ�����.
   * <p>PST��Ҫ���������жϣ����ж�Ϊtrueʱ��PST��Ҫ�ȴ�PSB��λ.</p>
   * @param srcPoint �����뿪����ʼ��.
   * @param orderName ��������ID
   * @return ���ҽ��������뿪����ʼ����ָ���������񼯵ĵ�һ�����ʱ������{@code true}.
   */
  boolean isLeavingFirstTrackPoint(Point srcPoint, String orderName);
  
  /**
   * �ж�ָ���Ĺ���Ƿ����޳����.
   * <p>ֻ���ж��޳�����б��Ƿ�����ù��</p>
   * @param psbTrack ���жϵĹ��
   * @return ��ǰ����ָ���Ĺ�����޳����ʱ������{@code true}.
   */
  boolean isNoVehicleTrack(int psbTrack);
}
