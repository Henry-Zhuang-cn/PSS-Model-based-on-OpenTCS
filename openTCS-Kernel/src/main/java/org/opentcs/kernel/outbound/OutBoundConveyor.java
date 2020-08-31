/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opentcs.kernel.outbound;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.opentcs.components.kernel.services.OutboundOrderService;
import org.opentcs.components.kernel.services.TimeFactorService;
import org.opentcs.customizations.kernel.GlobalSyncObject;
import org.opentcs.data.TCSObjectEvent;
import org.opentcs.data.TCSObjectReference;
import org.opentcs.data.model.Bin;
import org.opentcs.data.model.Bin.SKU;
import org.opentcs.data.model.Location;
import org.opentcs.data.order.OutboundOrder;
import org.opentcs.kernel.inbound.InboundConveyor;
import org.opentcs.kernel.workingset.TCSObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Henry
 */
public class OutboundConveyor 
    implements Runnable{
  
  private static final int ENTRANCE_TRACK = 3;
  
  private static final int TRACK_INTERVAL = 10000;
  
  public static final long RUN_INTERVAL = 10000;
  /**
   * This class's Logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(OutboundConveyor.class);
  /**
   * A global object to be used for synchronization within the kernel.
   */
  private final Object globalSyncObject;
  /**
   * The openTCS object pool.
   */
  private final TCSObjectPool objectPool;
  /**
   * The outbound working set.
   */
  private final OutboundWorkingSet outboundWorkingSet;
  /**
   * The inbound conveyor.
   */
  private final InboundConveyor inboundConveyor;
  /**
   * The outbound order service
   */
  private final OutboundOrderService outOrderService;
  /**
   * The simulation time factor service.
   */
  private final TimeFactorService timeFactorService;
  /**
   * A list represents all the bins on the conveyor.
   * BinEntry ��¼�˸�����ʵ��������ϴ��ʹ���ʱ��
   */
  private final List<BinEntry> binsOnConveyor = new ArrayList<>();

  @Inject
  public OutboundConveyor(@GlobalSyncObject Object globalSyncObject, 
                          TCSObjectPool objectPool,
                          OutboundWorkingSet outboundWorkingSet,
                          InboundConveyor inboundConveyor,
                          OutboundOrderService outOrderService,
                          TimeFactorService timeFactorService) {
    this.globalSyncObject = globalSyncObject;
    this.objectPool = objectPool;
    this.outboundWorkingSet = outboundWorkingSet;
    this.inboundConveyor = inboundConveyor;
    this.outOrderService = outOrderService;
    this.timeFactorService = timeFactorService;
  }

  public void clear(){
    binsOnConveyor.clear();
  }
  
  @Override
  public void run() {
    // ���׶�
    checkOutBoundStation();
    // ��ѡ�׶�
    pickingForOutBoundOrder();
  }
  
  private void checkOutBoundStation() {
    // ���׶Σ�
    // ����������վ�������ճ���վ�ഫ�ʹ���ڵľ������򣬽Ͻ��ĳ���վ���ȱ�����
    // ����վ�����������䣬��������ϴ��ʹ�������¼����ϴ��ʹ���ʱ��
    synchronized(globalSyncObject){
      objectPool
          .getObjects(Location.class,loc -> {
            return loc.getType().getName().equals(Location.OUT_BOUND_STATION_TYPE)
                && loc.stackSize() != 0;
            })
          .stream()
          .sorted((loc1,loc2) -> {
            Integer distance1 =  Math.abs(loc1.getPsbTrack() - ENTRANCE_TRACK);
            Integer distance2 = Math.abs(loc2.getPsbTrack() - ENTRANCE_TRACK);
            return distance1.compareTo(distance2);
          })
          .forEach(location -> {
            LOG.info("method called");
            // ��������ϴ��ʹ�������¼����ϴ��ʹ���ʱ��
            for(int i = 0;i<location.stackSize();i++)
              binsOnConveyor.add(new BinEntry(location.getBin(i)));
            Location newLocation = objectPool.replaceObject(location.withBins(new ArrayList<>()));
            objectPool.emitObjectEvent(newLocation,
                               location,
                               TCSObjectEvent.Type.OBJECT_MODIFIED);
          });
    }
  }
  
  private void pickingForOutBoundOrder() {
    // ��ѡ�׶Σ�
    // ��ǰ��˳�򣬱������ʹ��ϵ�ÿ�����䣬�����Ѿ��������㹻�����������зּ����
    // �ּ�ʱ��������������SKU�Լ����ⶩ���ļ�ѡ���ȣ��ּ���ɺ���������Ƿ�Ϊ��ѡ���Ƿ�ؿ�
    binsOnConveyor.stream().filter(this::hasMovedEnoughDistance).forEach(entry -> {
      synchronized(globalSyncObject){
        LOG.info("method called");
        Bin updatedBin = objectPool.getObject(Bin.class, entry.bin.getName());
        updateBinAndOutboundOrder(updatedBin);
        binsOnConveyor.remove(entry);
      }
    });
  }
  
  private void updateBinAndOutboundOrder(Bin bin) {
    // ���������Ԥ������м�ѡ��������������SKU��Ȼ����״̬��Ϊ�Ѽ�ѡ��
    // ��������Ӧ�ĳ��ⶩ���ļ�ѡ����
    
    for(TCSObjectReference<OutboundOrder> outOrderRef : outboundWorkingSet.getWorkingSets()){
      
      Set<SKU> requiredSkus = bin.getReservations().get(outOrderRef.getName());
      if(requiredSkus == null)
        continue;
      Map<String,Double> requiredSkuMap 
          = requiredSkus.stream().collect(Collectors.toMap(SKU::getSkuID,SKU::getQuantity));
      Set<SKU> updatedSkus = new HashSet<>();

      bin.getSKUs().forEach(sku -> {
        Double quantity = requiredSkuMap.get(sku.getSkuID());
        if(quantity != null)
          sku = new SKU(sku.getSkuID(), sku.getQuantity() - quantity);
        if(sku.getQuantity() > 0.0)
          updatedSkus.add(sku);
      });

      objectPool.removeObject(bin.getReference());
      // ���������Ƿ�Ϊ��ѡ���Ƿ�ؿ�
      if(!updatedSkus.isEmpty()){
        bin = bin.withSKUs(updatedSkus)
            .withState(Bin.State.Inbounding)
            .withAssignedTransportOrder(null);
        // ������ؿ�
        inboundConveyor.onConveyor(bin);
      }
      
      // ���³��ⶩ���ļ�ѡ����
      // ��������ɣ�����ӹ���̨�Ƴ���ѡ���¶������й���
      if(outOrderService.pickSKUs(outOrderRef, requiredSkus).getPickedCompletion() >= 1.0){
        outOrderService.updateOutboundOrderState(outOrderRef, OutboundOrder.State.FINISHED);
        outboundWorkingSet.removePickingOrder(outOrderRef);
        outboundWorkingSet.enableOutboundOrder();
      }
    }
  }

  private boolean hasMovedEnoughDistance(BinEntry entry) {
    // ���ݵ�ǰʱ�������䱻���ϴ��ʹ���ʱ��֮������ʾ�����ƶ��ľ���
    // �㹻�ľ�����������ԭ���ĳ���վ���ڹ��봫�ʹ���ڵľ����������
    return Instant.now().toEpochMilli() - entry.enterTime.toEpochMilli()
        >= Math.abs(entry.bin.getPsbTrack() - ENTRANCE_TRACK) 
        * TRACK_INTERVAL / timeFactorService.getSimulationTimeFactor();
  }
  
  public long getRunInterval(){
    return RUN_INTERVAL / (long)timeFactorService.getSimulationTimeFactor();
  }
  
  private static final class BinEntry{
    private final Bin bin;
    private final Instant enterTime;

    public BinEntry(Bin bin) {
      this.enterTime = Instant.now();
      this.bin = bin;
    }
  }
}
