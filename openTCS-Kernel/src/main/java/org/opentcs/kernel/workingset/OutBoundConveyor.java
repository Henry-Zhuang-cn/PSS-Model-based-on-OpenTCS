/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.opentcs.kernel.workingset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.opentcs.components.kernel.services.TimeFactorService;
import org.opentcs.customizations.kernel.GlobalSyncObject;
import org.opentcs.data.TCSObjectEvent;
import org.opentcs.data.model.Bin;
import org.opentcs.data.model.Location;
import org.opentcs.data.order.BinOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Henry
 */
public class OutBoundConveyor 
    implements Runnable{
  
  private static final int ENTRANCE_TRACK = 3;
  private static final int MOVING_TIME_UNIT = 1000;
  
  public static final long INTERVAL_TIME = 5000;
  /**
   * This class's Logger.
   */
  private static final Logger LOG = LoggerFactory.getLogger(OutBoundConveyor.class);
  /**
   * A global object to be used for synchronization within the kernel.
   */
  private final Object globalSyncObject;
  /**
   * The openTCS object pool.
   */
  private final TCSObjectPool objectPool;
  /**
   * The simulation time factor service.
   */
  private final TimeFactorService timeFactorService;
  /**
   * A list represents all the bins on the conveyor.
   * PickEntry ��¼�˸�����ʵ��������ϴ��ʹ���ʱ��
   */
  private final List<BinEntry> binsOnConveyor = new ArrayList<>();

  @Inject
  public OutBoundConveyor(@GlobalSyncObject Object globalSyncObject, 
                          TCSObjectPool objectPool,
                          TimeFactorService timeFactorService) {
    this.globalSyncObject = globalSyncObject;
    this.objectPool = objectPool;
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
    pickingForCustomerOrder();
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
  
  private void pickingForCustomerOrder() {
    // ��ѡ�׶Σ�
    // ��ǰ��˳�򣬱������ʹ��ϵ�ÿ�����䣬�����Ѿ��������㹻�����������зּ����
    // �ּ�ʱ��������������SKU�Լ��ͻ������ĵ�ǰ��ɽ��ȣ��ּ���ɺ���������Ƿ�Ϊ��ѡ���Ƿ�ؿ�
    binsOnConveyor.stream().filter(this::hasMovedEnoughDistance).forEach(entry -> {
      synchronized(globalSyncObject){
        LOG.info("method called");
        Bin updatedBin = objectPool.getObject(Bin.class, entry.bin.getName());
        updateBinAndCustomerOrder(updatedBin);
        binsOnConveyor.remove(entry);
      }
    });
  }
  
  private void updateBinAndCustomerOrder(Bin bin) {
    // ���ݿͻ�����Ҫ���SKU���м�ѡ��������������SKU��Ȼ����״̬��Ϊ�Ѽ�ѡ��
    // ��������Ӧ�Ŀͻ�������ɽ���
    BinOrder currBinOrder = objectPool
        .getObject(BinOrder.class,bin.getAssignedBinOrder());
    
    Map<String,Integer> requiredSkus = currBinOrder.getRequiredSku();
    Set<Bin.SKU> updatedSkus = new HashSet<>();
    
    bin.getSKUs().forEach(sku -> {
      Integer quantity = requiredSkus.get(sku.getSkuID());
      if(quantity != null)
        sku = new Bin.SKU(sku.getSkuID(), sku.getQuantity() - quantity);
      if(sku.getQuantity() > 0)
        updatedSkus.add(sku);
    });
    
    if(updatedSkus.isEmpty())
      objectPool.removeObject(bin.getReference());
    else{
      objectPool.replaceObject(bin.withSKUs(updatedSkus).withState(Bin.State.Picked).withAssignedBinOrder(null));
      // ������ؿ�
    }
    
    // ���¿ͻ�������ɽ��� ������ɣ�
    
  }

  private boolean hasMovedEnoughDistance(BinEntry entry) {
    // ���ݵ�ǰʱ�������䱻���ϴ��ʹ���ʱ��֮������ʾ�����ƶ��ľ���
    // �㹻�ľ�����������ԭ���ĳ���վ���ڹ��봫�ʹ���ڵľ����������
    return Instant.now().toEpochMilli() - entry.enterTime.toEpochMilli()
        >= Math.abs(entry.bin.getPsbTrack() - ENTRANCE_TRACK) 
        * MOVING_TIME_UNIT / timeFactorService.getSimulationTimeFactor();
  }
  
  public long getIntervalTime(){
    return INTERVAL_TIME / (long)timeFactorService.getSimulationTimeFactor();
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
